#!/usr/bin/env python3
"""Generate Google Play Store listing assets for Garage Unlock.

SPDX-License-Identifier: MIT
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "play-store"

# Brand palette (matches app themes.xml / activity_main.xml)
BG = "#121826"
GREEN = "#2E7D32"
GREEN_LIGHT = "#43A047"
WHITE = "#FFFFFF"
TEXT_SECONDARY = "#B0BEC5"
TEXT_HINT = "#90A4AE"
BLUE = "#1565C0"
SUCCESS = "#81C784"
DIVIDER = "#37474F"
TINT = "#546E7A"


def hex_rgb(color: str) -> tuple[int, int, int]:
    color = color.lstrip("#")
    return tuple(int(color[i : i + 2], 16) for i in (0, 2, 4))


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial Bold.ttf" if bold else "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def draw_garage_icon(draw: ImageDraw.ImageDraw, cx: float, cy: float, scale: float) -> None:
    """Render the ic_launcher garage door shape centered at (cx, cy)."""
    # Outer green roof/body (from ic_launcher viewport 108)
    outer = [
        (18, 78), (18, 42), (54, 24), (90, 42), (90, 78),
    ]
    inner = [
        (30, 78), (30, 48), (54, 36), (78, 48), (78, 78),
    ]
    door = [
        (48, 78), (48, 58), (60, 58), (60, 78),
    ]

    def tx(x: float, y: float) -> tuple[float, float]:
        return (cx + (x - 54) * scale, cy + (y - 54) * scale)

    draw.polygon([tx(x, y) for x, y in outer], fill=hex_rgb(GREEN))
    draw.polygon([tx(x, y) for x, y in inner], fill=hex_rgb(WHITE))
    draw.polygon([tx(x, y) for x, y in door], fill=hex_rgb(BG))


def generate_app_icon() -> Path:
    size = 512
    img = Image.new("RGB", (size, size), hex_rgb(BG))
    draw = ImageDraw.Draw(img)
    # Adaptive icon safe zone: ~66% of canvas; scale from 72-unit width shape
    draw_garage_icon(draw, size / 2, size / 2, size / 72 * 2.2)
    path = OUT / "app-icon-512.png"
    img.save(path, "PNG", optimize=True)
    return path


def generate_feature_graphic() -> Path:
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), hex_rgb(BG))
    draw = ImageDraw.Draw(img)

    # Subtle gradient bands
    for y in range(h):
        t = y / h
        r = int(18 + 8 * math.sin(t * math.pi))
        g = int(24 + 6 * math.sin(t * math.pi))
        b = int(38 + 10 * math.sin(t * math.pi))
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # Accent stripe
    draw.rectangle([0, h - 6, w, h], fill=hex_rgb(GREEN))

    draw_garage_icon(draw, 180, h / 2 + 10, 3.8)

    title_font = load_font(64, bold=True)
    subtitle_font = load_font(28)
    tag_font = load_font(22)

    draw.text((340, 140), "Garage Unlock", fill=hex_rgb(WHITE), font=title_font)
    draw.text((340, 220), "One tap from Android Auto", fill=hex_rgb(TEXT_SECONDARY), font=subtitle_font)
    draw.text((340, 280), "Open your garage door safely while driving", fill=hex_rgb(TEXT_HINT), font=tag_font)

    # Decorative green pill
    pill_y = 340
    draw.rounded_rectangle([340, pill_y, 620, pill_y + 44], radius=22, fill=hex_rgb(GREEN))
    pill_font = load_font(20, bold=True)
    draw.text((365, pill_y + 10), "Android Auto ready", fill=hex_rgb(WHITE), font=pill_font)

    path = OUT / "feature-graphic-1024x500.png"
    img.save(path, "PNG", optimize=True)
    return path


def draw_rounded_rect(draw: ImageDraw.ImageDraw, box: tuple, radius: int, fill: str) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=hex_rgb(fill))


def draw_phone_frame(content: Image.Image) -> Image.Image:
    """Wrap screenshot content in a minimal status bar + phone chrome."""
    w, h = content.size
    frame_h = h + 80
    frame = Image.new("RGB", (w, frame_h), hex_rgb(BG))
    frame.paste(content, (0, 80))

    draw = ImageDraw.Draw(frame)
    status_font = load_font(22)
    draw.text((24, 28), "9:41", fill=hex_rgb(WHITE), font=status_font)
    draw.text((w - 120, 28), "●●●", fill=hex_rgb(TEXT_SECONDARY), font=status_font)
    return frame


def draw_main_screen(
    *,
    pass_value: str = "",
    pass_status: str = "",
    status: str = "Ready to unlock",
    button_label: str = "Open Garage North Coiling Door",
    button_color: str = GREEN,
) -> Image.Image:
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), hex_rgb(BG))
    draw = ImageDraw.Draw(img)

    pad = 48
    y = 120

    title_font = load_font(52, bold=True)
    body_font = load_font(32)
    hint_font = load_font(28)
    status_font = load_font(36)
    btn_font = load_font(36, bold=True)
    small_font = load_font(30)

    draw.text((pad, y), "Garage Unlock", fill=hex_rgb(WHITE), font=title_font)
    y += 80

    hint = (
        "When your Alta guest pass rotates, paste the new link here. "
        "Android Auto uses the same saved pass."
    )
    # Word-wrap hint
    words = hint.split()
    lines: list[str] = []
    line = ""
    for word in words:
        test = f"{line} {word}".strip()
        bbox = draw.textbbox((0, 0), test, font=body_font)
        if bbox[2] - bbox[0] > w - 2 * pad and line:
            lines.append(line)
            line = word
        else:
            line = test
    if line:
        lines.append(line)
    for ln in lines:
        draw.text((pad, y), ln, fill=hex_rgb(TEXT_SECONDARY), font=body_font)
        y += 44
    y += 24

    # Input field
    field_h = 72
    draw_rounded_rect(draw, (pad, y, w - pad, y + field_h), 8, "#1E2936")
    draw.rectangle([pad, y, pad + 4, y + field_h], fill=hex_rgb(TINT))
    input_text = pass_value or "Guest pass link or short code"
    input_color = TEXT_SECONDARY if pass_value else TEXT_HINT
    draw.text((pad + 20, y + 18), input_text, fill=hex_rgb(input_color), font=hint_font)
    y += field_h + 24

    # Save button
    btn_h = 72
    draw_rounded_rect(draw, (pad, y, w - pad, y + btn_h), 8, BLUE)
    save_bbox = draw.textbbox((0, 0), "Save guest pass", font=btn_font)
    save_w = save_bbox[2] - save_bbox[0]
    draw.text(((w - save_w) / 2, y + 16), "Save guest pass", fill=hex_rgb(WHITE), font=btn_font)
    y += btn_h + 16

    if pass_status:
        draw.text((pad, y), pass_status, fill=hex_rgb(SUCCESS), font=small_font)
        y += 48
    else:
        y += 24

    # Divider
    draw.line([(pad, y), (w - pad, y)], fill=hex_rgb(DIVIDER), width=2)
    y += 48

    status_bbox = draw.textbbox((0, 0), status, font=status_font)
    status_w = status_bbox[2] - status_bbox[0]
    draw.text(((w - status_w) / 2, y), status, fill=hex_rgb(TEXT_SECONDARY), font=status_font)
    y += 72

    unlock_h = 96
    draw_rounded_rect(draw, (pad, y, w - pad, y + unlock_h), 12, button_color)
    btn_bbox = draw.textbbox((0, 0), button_label, font=btn_font)
    btn_w = btn_bbox[2] - btn_bbox[0]
    draw.text(((w - btn_w) / 2, y + 26), button_label, fill=hex_rgb(WHITE), font=btn_font)

    return img


def draw_android_auto_screen(*, unlocking: bool = False) -> Image.Image:
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), "#0A0A0A")
    draw = ImageDraw.Draw(img)

    # Car display bezel
    pad = 60
    display = (pad, 200, w - pad, h - 280)
    draw.rounded_rectangle(display, radius=24, fill="#1A1A1A", outline="#333333", width=3)

    title_font = load_font(40, bold=True)
    item_font = load_font(34, bold=True)
    sub_font = load_font(26)

    dx1, dy1, dx2, dy2 = display
    draw.text((dx1 + 32, dy1 + 28), "Garage Unlock", fill=hex_rgb(WHITE), font=title_font)

    card_y = dy1 + 120
    card_h = 180
    draw.rounded_rectangle([dx1 + 32, card_y, dx2 - 32, card_y + card_h], radius=16, fill="#2A2A2A")

    draw_garage_icon(draw, dx1 + 100, card_y + card_h / 2, 1.6)

    title = "Unlocking..." if unlocking else "Garage North Coiling Door"
    subtitle = "Sending unlock request..." if unlocking else "Ready"
    draw.text((dx1 + 160, card_y + 52), title, fill=hex_rgb(WHITE), font=item_font)
    draw.text((dx1 + 160, card_y + 100), subtitle, fill=hex_rgb(TEXT_SECONDARY), font=sub_font)

    if unlocking:
        # Simple spinner arc
        cx, cy = dx2 - 80, card_y + card_h / 2
        r = 28
        for i in range(8):
            angle = i * math.pi / 4
            x1 = cx + r * 0.5 * math.cos(angle)
            y1 = cy + r * 0.5 * math.sin(angle)
            x2 = cx + r * math.cos(angle)
            y2 = cy + r * math.sin(angle)
            alpha = 80 + i * 20
            draw.line([(x1, y1), (x2, y2)], fill=(alpha, alpha, alpha), width=4)

    caption_font = load_font(28)
    caption = "Android Auto in-car experience"
    cb = draw.textbbox((0, 0), caption, font=caption_font)
    cw = cb[2] - cb[0]
    draw.text(((w - cw) / 2, h - 180), caption, fill=hex_rgb(TEXT_HINT), font=caption_font)

    return img


def generate_screenshots() -> list[Path]:
    paths: list[Path] = []

    screens = [
        ("screenshot-01-main-ready.png", draw_main_screen()),
        (
            "screenshot-02-pass-saved.png",
            draw_main_screen(
                pass_value="https://access.alta.avigilon.com/cloudKeyUnlock?shortCode=YOUR_SHORT_CODE",
                pass_status="Guest pass saved. Short code: YOUR_SHORT_CODE",
            ),
        ),
        ("screenshot-03-android-auto.png", draw_android_auto_screen(unlocking=False)),
    ]

    for name, content in screens:
        framed = draw_phone_frame(content)
        # Scale to 1080x1920 (9:16) — frame adds status bar; crop/fit
        target_w, target_h = 1080, 1920
        framed = framed.resize((target_w, target_h), Image.Resampling.LANCZOS)
        path = OUT / name
        framed.save(path, "PNG", optimize=True)
        paths.append(path)

    return paths


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)

    icon = generate_app_icon()
    feature = generate_feature_graphic()
    screenshots = generate_screenshots()

    print(f"Generated Play Store assets in {OUT}/\n")
    for p in [icon, feature, *screenshots]:
        size_kb = p.stat().st_size / 1024
        with Image.open(p) as im:
            print(f"  {p.name}: {im.size[0]}x{im.size[1]}, {size_kb:.1f} KB")


if __name__ == "__main__":
    main()
