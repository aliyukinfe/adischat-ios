from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
MAP_PATH = ROOT / "scripts" / "adishop-seed-image-map.json"
OUTPUT_DIR = ROOT / "assets" / "shop-seed"
SIZE = 900


PALETTES = {
    "bluetooth-speaker": ("#EBF3F6", "#22343C", "#7BA6B2"),
    "business-notebook": ("#F4EFE8", "#1F2933", "#BFA37B"),
    "canvas-tote-bag": ("#F7EFE2", "#6B533F", "#D6B98C"),
    "ceramic-coffee-cups": ("#F4F6F2", "#30302B", "#B8C4B1"),
    "classic-cotton-t-shirt": ("#F2F5F8", "#274052", "#9FB8C8"),
    "cotton-towel-set": ("#F8F1EB", "#4F4A45", "#D8BBA5"),
    "daily-planner": ("#F6F1FA", "#2E2745", "#BCA9D7"),
    "daily-skin-cream": ("#FFF3EF", "#5C3D36", "#E7B6A6"),
    "honey-jar": ("#FFF6DD", "#6A4618", "#E4A728"),
    "kids-puzzle-set": ("#F0F7FF", "#27364A", "#E8876A"),
    "light-jacket": ("#EEF4F1", "#263A33", "#88A69A"),
    "natural-soap-pack": ("#F7F3E8", "#4B4638", "#C9B76E"),
    "phone-stand": ("#F3F4F6", "#1F2937", "#9AA4B2"),
    "premium-coffee-beans": ("#F4EDE7", "#3A261C", "#9B6A43"),
    "smart-watch": ("#EEF2FF", "#23263A", "#8391D6"),
    "spice-mix": ("#FFF0E7", "#5F281B", "#DB7142"),
    "table-organizer": ("#F6F2EA", "#3D3328", "#C8A46A"),
    "training-water-bottle": ("#EAF7F5", "#1F3E43", "#63B6AD"),
    "usb-c-fast-charger": ("#F5F5F5", "#2E3035", "#A8AAB2"),
    "wireless-earbuds": ("#EFF7FB", "#24313A", "#7BB7CC"),
}


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def rounded_rectangle(draw: ImageDraw.ImageDraw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def draw_product(draw: ImageDraw.ImageDraw, slug: str, fg: str, accent: str):
    cx, cy = SIZE // 2, 430
    if "earbuds" in slug:
        draw.ellipse((310, 300, 420, 410), fill=fg)
        draw.ellipse((480, 300, 590, 410), fill=fg)
        draw.rounded_rectangle((360, 430, 540, 500), radius=34, fill=accent)
    elif "speaker" in slug:
        rounded_rectangle(draw, (260, 285, 640, 535), 70, fg)
        for x in (360, 540):
            draw.ellipse((x - 58, 360, x + 58, 476), fill=accent)
        draw.rectangle((324, 305, 576, 338), fill=accent)
    elif "notebook" in slug or "planner" in slug:
        rounded_rectangle(draw, (300, 235, 610, 585), 26, "#FFFFFF", fg, 6)
        draw.line((360, 260, 360, 560), fill=accent, width=12)
        for y in range(330, 505, 46):
            draw.line((395, y, 560, y), fill=accent, width=6)
    elif "tote" in slug:
        rounded_rectangle(draw, (300, 310, 610, 595), 34, accent)
        draw.arc((365, 210, 545, 430), start=190, end=350, fill=fg, width=16)
    elif "cups" in slug:
        for x in (290, 455):
            rounded_rectangle(draw, (x, 330, x + 150, 520), 34, "#FFFFFF", fg, 6)
            draw.arc((x + 110, 370, x + 220, 475), -70, 80, fill=accent, width=14)
        draw.ellipse((285, 510, 620, 555), fill=accent)
    elif "t-shirt" in slug:
        pts = [(330, 285), (410, 235), (490, 235), (570, 285), (535, 370), (535, 585), (365, 585), (365, 370)]
        draw.polygon(pts, fill=accent)
        draw.line(pts + [pts[0]], fill=fg, width=8)
    elif "towel" in slug:
        for i, color in enumerate([fg, accent, "#FFFFFF"]):
            rounded_rectangle(draw, (285 + i * 70, 300 + i * 38, 605 + i * 10, 480 + i * 38), 34, color)
    elif "cream" in slug:
        rounded_rectangle(draw, (330, 300, 570, 540), 48, "#FFFFFF", fg, 6)
        rounded_rectangle(draw, (380, 250, 520, 315), 20, accent)
        draw.ellipse((370, 360, 530, 500), fill=accent)
    elif "honey" in slug:
        rounded_rectangle(draw, (335, 285, 565, 570), 44, "#F6C256", fg, 6)
        rounded_rectangle(draw, (380, 230, 520, 302), 22, accent)
        draw.ellipse((370, 370, 530, 490), fill="#FFE49E")
    elif "puzzle" in slug:
        colors = [fg, accent, "#F3C969", "#7FB3A9"]
        for i, (x, y) in enumerate([(310, 295), (455, 295), (310, 440), (455, 440)]):
            rounded_rectangle(draw, (x, y, x + 135, y + 135), 24, colors[i])
    elif "jacket" in slug:
        pts = [(325, 275), (400, 230), (500, 230), (575, 275), (540, 585), (360, 585)]
        draw.polygon(pts, fill=accent)
        draw.line(pts + [pts[0]], fill=fg, width=8)
        draw.line((450, 245, 450, 575), fill=fg, width=7)
    elif "soap" in slug:
        for i, color in enumerate([accent, "#FFFFFF", fg]):
            rounded_rectangle(draw, (290 + i * 80, 340 + i * 45, 470 + i * 80, 455 + i * 45), 38, color)
    elif "phone-stand" in slug:
        rounded_rectangle(draw, (340, 250, 555, 515), 30, "#FFFFFF", fg, 8)
        draw.polygon([(325, 570), (575, 570), (515, 510), (385, 510)], fill=accent)
    elif "coffee" in slug:
        for x in (330, 395, 460):
            draw.ellipse((x, 360, x + 90, 450), fill=fg)
        rounded_rectangle(draw, (310, 460, 590, 580), 26, accent)
    elif "watch" in slug:
        rounded_rectangle(draw, (390, 205, 510, 640), 42, fg)
        rounded_rectangle(draw, (330, 330, 570, 530), 48, "#FFFFFF", fg, 8)
        draw.ellipse((395, 380, 505, 490), fill=accent)
    elif "spice" in slug:
        rounded_rectangle(draw, (320, 310, 580, 560), 42, "#FFFFFF", fg, 6)
        for x, color in [(360, "#D94B32"), (440, "#E5A12D"), (520, "#7E3F24")]:
            draw.ellipse((x - 34, 375, x + 34, 443), fill=color)
    elif "organizer" in slug:
        rounded_rectangle(draw, (290, 330, 610, 565), 28, accent, fg, 6)
        for x in (350, 450, 550):
            draw.line((x, 345, x, 552), fill=fg, width=6)
    elif "bottle" in slug:
        rounded_rectangle(draw, (385, 250, 515, 590), 48, accent, fg, 7)
        rounded_rectangle(draw, (410, 205, 490, 265), 18, fg)
        draw.line((395, 390, 505, 390), fill="#FFFFFF", width=8)
    elif "charger" in slug:
        rounded_rectangle(draw, (330, 285, 570, 520), 42, "#FFFFFF", fg, 7)
        draw.rectangle((382, 235, 420, 292), fill=fg)
        draw.rectangle((480, 235, 518, 292), fill=fg)
        draw.line((450, 360, 410, 450, 480, 450, 440, 535), fill=accent, width=16)
    else:
        draw.ellipse((cx - 145, cy - 145, cx + 145, cy + 145), fill=accent)
        draw.rectangle((cx - 70, cy - 70, cx + 70, cy + 70), fill=fg)


def make_image(item: dict[str, str]):
    slug = item["product_slug"]
    bg, fg, accent = PALETTES[slug]
    image = Image.new("RGB", (SIZE, SIZE), bg)
    draw = ImageDraw.Draw(image)

    draw.ellipse((120, 120, 780, 780), fill="#FFFFFF")
    draw.ellipse((165, 170, 735, 740), outline="#E8E2D8", width=5)
    draw_product(draw, slug, fg, accent)

    title_font = font(46)
    label_font = font(24)
    title = item["product_title"]
    draw.text((SIZE // 2, 720), title, font=title_font, fill=fg, anchor="mm")
    draw.text((SIZE // 2, 770), "AdisShop Marketplace", font=label_font, fill="#7A7166", anchor="mm")

    out = ROOT / item["local_source"]
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out, "WEBP", quality=86, method=6)
    return out


def main():
    entries = json.loads(MAP_PATH.read_text(encoding="utf-8"))
    if len(entries) != 20:
        raise SystemExit(f"Expected 20 mapped products, found {len(entries)}")
    paths = [make_image(entry) for entry in entries]
    for path in paths:
        size = path.stat().st_size
        if size > 300_000:
            raise SystemExit(f"{path} is {size} bytes, above 300 KB")
    print(f"Generated {len(paths)} WebP assets in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
