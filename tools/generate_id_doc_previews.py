from __future__ import annotations

from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path("/Users/leo/AndroidStudioProjects/JuZiScan")
OUT_DIR = ROOT / "app/src/main/res/drawable-nodpi"
PREVIEW_PATH = ROOT / "tmp/id_doc_preview_sheet.png"
FONT_PATH = Path("/System/Library/Fonts/Hiragino Sans GB.ttc")


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    try:
        return ImageFont.truetype(str(FONT_PATH), size)
    except OSError:
        return ImageFont.load_default()


def rounded_panel(size: tuple[int, int], fill: str, stroke: str, radius: int = 16) -> Image.Image:
    width, height = size
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))

    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.rounded_rectangle(
        (10, 10, width - 10, height - 10),
        radius=radius,
        fill=(183, 213, 255, 80),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(8))
    canvas.alpha_composite(glow)

    panel = Image.new("RGBA", size, (0, 0, 0, 0))
    panel_draw = ImageDraw.Draw(panel)
    panel_draw.rounded_rectangle(
        (13, 13, width - 13, height - 13),
        radius=radius,
        fill=fill,
        outline=stroke,
        width=3,
    )
    canvas.alpha_composite(panel)
    return canvas


def bank_card(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#F6D79D", "#F3E4B4", radius=14)
    draw = ImageDraw.Draw(img)
    w, h = size
    draw.rounded_rectangle((26, 52, 60, 74), radius=5, fill=(245, 220, 160, 255))
    draw.line((28, 92, w - 32, 92), fill=(237, 191, 126, 220), width=4)
    draw.line((30, 108, w - 54, 108), fill=(236, 198, 142, 180), width=3)
    return img


def identity_card(size: tuple[int, int], title: str | None = None, seal: bool = False) -> Image.Image:
    img = rounded_panel(size, "#F4FAFF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    line = (149, 184, 226, 255)
    avatar = (80, 99, 130, 255)
    skin = (249, 214, 190, 255)

    if title:
        font = load_font(16)
        draw.text((24, 22), title, fill=(138, 166, 210, 255), font=font)

    for y in (42, 62):
        draw.rounded_rectangle((28, y, w * 0.6, y + 6), radius=3, fill=line)
    for y in (86, 108, 130):
        draw.rounded_rectangle((28, y, w * 0.74, y + 6), radius=3, fill=line)

    head_x = int(w * 0.74)
    head_y = int(h * 0.64)
    draw.ellipse((head_x - 18, head_y - 36, head_x + 18, head_y), fill=avatar)
    draw.ellipse((head_x - 14, head_y - 18, head_x + 14, head_y + 8), fill=skin)
    draw.rounded_rectangle((head_x - 26, head_y + 4, head_x + 26, head_y + 28), radius=12, fill=(204, 226, 250, 255))

    if seal:
        seal_box = (18, 20, 68, 70)
        draw.ellipse(seal_box, outline=(220, 152, 165, 220), width=3)
        draw.line((24, 54, 62, 36), fill=(220, 152, 165, 180), width=2)
        draw.line((22, 34, 64, 56), fill=(220, 152, 165, 180), width=2)

    return img


def household_register(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#EFF6FF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    split = int(h * 0.52)

    draw.rounded_rectangle((13, 13, w - 13, split - 6), radius=16, outline="#C6D8F4", width=3)
    draw.rounded_rectangle((13, split + 6, w - 13, h - 13), radius=16, outline="#C6D8F4", width=3)
    draw.line((16, split, w - 16, split), fill=(190, 210, 238, 255), width=3)

    top_box = (int(w * 0.10), int(h * 0.12), int(w * 0.38), int(h * 0.30))
    draw.rectangle(top_box, outline=(193, 205, 221, 255), width=2)
    draw.line((top_box[0], top_box[1] + 18, top_box[2], top_box[1] + 18), fill=(193, 205, 221, 255), width=2)
    draw.ellipse((24, 30, 68, 74), outline=(220, 152, 165, 220), width=4)
    for x in range(0, 6):
        px = int(w * 0.46) + x * 18
        draw.line((px, 28, px, split - 20), fill=(214, 225, 240, 255), width=2)
    for y in range(38, split - 18, 16):
        draw.line((int(w * 0.43), y, w - 26, y), fill=(214, 225, 240, 255), width=2)

    for x in range(0, 7):
        px = 32 + x * 34
        draw.line((px, split + 26, px, h - 34), fill=(214, 225, 240, 255), width=2)
    for y in range(split + 42, h - 34, 18):
        draw.line((26, y, w - 24, y), fill=(214, 225, 240, 255), width=2)
    return img


def invoice(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#FFF2CF", "#D7BC7A", radius=14)
    draw = ImageDraw.Draw(img)
    w, h = size
    left, top, right, bottom = 22, 22, w - 22, h - 22
    draw.rounded_rectangle((left, top, right, bottom), radius=10, outline=(200, 171, 104, 255), width=3)
    for x in (int(w * 0.24), int(w * 0.48), int(w * 0.72)):
        draw.line((x, top + 42, x, bottom - 24), fill=(216, 192, 133, 220), width=2)
    for y in (top + 42, int(h * 0.54), bottom - 26):
        draw.line((left + 14, y, right - 14, y), fill=(216, 192, 133, 220), width=2)
    draw.arc((int(w * 0.35), 6, int(w * 0.65), 58), 5, 175, fill=(179, 124, 84, 220), width=4)
    draw.arc((int(w * 0.70), int(h * 0.62), int(w * 0.93), int(h * 0.88)), 10, 350, fill=(212, 126, 106, 220), width=4)
    font = load_font(18)
    draw.text((int(w * 0.43), 18), "XXXXX", fill=(174, 145, 97, 255), font=font)
    return img


def passport(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#F4FAFF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    draw.ellipse((w // 2 - 18, 28, w // 2 + 18, 62), outline=(160, 188, 228, 255), width=3)
    draw.line((w // 2, 30, w // 2, 60), fill=(160, 188, 228, 255), width=2)
    draw.arc((w // 2 - 18, 34, w // 2 + 18, 56), 10, 170, fill=(160, 188, 228, 255), width=2)
    draw.arc((w // 2 - 18, 34, w // 2 + 18, 56), 190, 350, fill=(160, 188, 228, 255), width=2)
    for y in (92, 130, 168):
        draw.rounded_rectangle((42, y, w - 42, y + 8), radius=4, fill=(160, 188, 228, 255))
    draw.ellipse((76, h - 136, 132, h - 80), fill=(80, 99, 130, 255))
    draw.ellipse((84, h - 118, 124, h - 70), fill=(249, 214, 190, 255))
    draw.rounded_rectangle((64, h - 72, 144, h - 26), radius=18, fill=(204, 226, 250, 255))
    return img


def driver_license(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#EFF6FF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    third = w // 2
    draw.line((third, 22, third, h - 22), fill=(193, 205, 221, 255), width=2)
    for y in (34, 54, 74, 94):
        draw.line((22, y, third - 16, y), fill=(160, 188, 228, 255), width=4)
    for y in (34, 54, 74, 94):
        draw.line((third + 18, y, w - 22, y), fill=(214, 225, 240, 255), width=3)
    car_top = h - 52
    draw.rounded_rectangle((w - 114, car_top, w - 48, car_top + 24), radius=10, fill=(163, 196, 236, 255))
    draw.polygon(
        [(w - 104, car_top), (w - 88, car_top - 14), (w - 62, car_top - 14), (w - 50, car_top)],
        fill=(163, 196, 236, 255),
    )
    draw.ellipse((w - 104, car_top + 16, w - 88, car_top + 32), fill=(104, 133, 175, 255))
    draw.ellipse((w - 72, car_top + 16, w - 56, car_top + 32), fill=(104, 133, 175, 255))
    return img


def vehicle_license(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#EFF6FF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    left, top, right, bottom = 20, 20, w - 20, h - 20
    mid_x = (left + right) // 2
    mid_y = (top + bottom) // 2

    draw.line((mid_x, top + 8, mid_x, bottom - 8), fill=(193, 205, 221, 255), width=2)
    draw.line((left + 8, mid_y, right - 8, mid_y), fill=(193, 205, 221, 255), width=2)

    for y in (36, 54, 72):
        draw.rounded_rectangle((34, y, mid_x - 20, y + 5), radius=3, fill=(160, 188, 228, 255))
    draw.ellipse((28, 26, 48, 46), outline=(212, 126, 146, 220), width=2)
    draw.line((mid_x + 24, 32, right - 26, 32), fill=(214, 225, 240, 255), width=2)
    for x in range(mid_x + 24, right - 18, 18):
        draw.line((x, 36, x, mid_y - 14), fill=(214, 225, 240, 255), width=2)
    for y in range(46, mid_y - 14, 12):
        draw.line((mid_x + 24, y, right - 24, y), fill=(214, 225, 240, 255), width=2)

    for y in (mid_y + 18, mid_y + 36, mid_y + 54):
        draw.rounded_rectangle((34, y, mid_x - 20, y + 5), radius=3, fill=(160, 188, 228, 255))
    for x in range(mid_x + 18, right - 18, 20):
        draw.line((x, mid_y + 18, x, bottom - 18), fill=(214, 225, 240, 255), width=2)
    for y in range(mid_y + 32, bottom - 16, 12):
        draw.line((mid_x + 14, y, right - 18, y), fill=(214, 225, 240, 255), width=2)

    car_top = bottom - 30
    draw.rounded_rectangle((right - 84, car_top - 8, right - 32, car_top + 10), radius=8, fill=(163, 196, 236, 255))
    draw.polygon(
        [(right - 78, car_top - 8), (right - 66, car_top - 20), (right - 46, car_top - 20), (right - 36, car_top - 8)],
        fill=(163, 196, 236, 255),
    )
    draw.ellipse((right - 76, car_top + 4, right - 64, car_top + 16), fill=(104, 133, 175, 255))
    draw.ellipse((right - 50, car_top + 4, right - 38, car_top + 16), fill=(104, 133, 175, 255))
    return img


def property_certificate(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#EFF6FF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    split = int(w * 0.58)
    draw.line((split, 24, split, h - 24), fill=(193, 205, 221, 255), width=2)
    for y in (36, 56, 76, 96):
        draw.rounded_rectangle((26, y, split - 18, y + 5), radius=3, fill=(160, 188, 228, 255))
    for x in range(split + 18, w - 22, 20):
        draw.line((x, 28, x, h - 28), fill=(214, 225, 240, 255), width=2)
    for y in range(38, h - 24, 14):
        draw.line((split + 10, y, w - 24, y), fill=(214, 225, 240, 255), width=2)
    return img


def business_license(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#F7F3E8", "#BFA874", radius=18)
    draw = ImageDraw.Draw(img)
    w, h = size
    draw.rounded_rectangle((16, 16, w - 16, h - 16), radius=16, outline=(191, 168, 116, 255), width=3)
    font = load_font(24)
    draw.text((28, 28), "营业执照", fill=(133, 108, 62, 255), font=font)
    draw.ellipse((26, 72, 62, 108), outline=(191, 168, 116, 255), width=3)
    for y in (76, 96, 116, 136):
        draw.rounded_rectangle((76, y, 188 if y < 136 else 212, y + 6), radius=3, fill=(175, 150, 93, 255))
    draw.rectangle((w - 42, h - 42, w - 22, h - 22), outline=(175, 150, 93, 255), width=2)
    return img


def diploma(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#F4FAFF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    font = load_font(22)
    draw.text((30, 24), "毕业证书", fill=(138, 166, 210, 255), font=font)
    draw.ellipse((24, 22, 64, 62), outline=(220, 152, 165, 220), width=3)
    draw.line((30, 52, 58, 34), fill=(220, 152, 165, 180), width=2)
    draw.line((28, 34, 60, 52), fill=(220, 152, 165, 180), width=2)
    head_x = int(w * 0.84)
    draw.ellipse((head_x - 18, h - 84, head_x + 18, h - 48), fill=(80, 99, 130, 255))
    draw.ellipse((head_x - 14, h - 66, head_x + 14, h - 20), fill=(249, 214, 190, 255))
    draw.rounded_rectangle((head_x - 26, h - 28, head_x + 26, h - 2), radius=12, fill=(204, 226, 250, 255))
    for idx, y in enumerate((46, 70, 96, 120)):
        extra = 38 if idx % 2 == 0 else 72
        draw.rounded_rectangle((86, y, w - extra, y + 5), radius=3, fill=(160, 188, 228, 255))
    for y in (150, 176):
        draw.rounded_rectangle((30, y, w - 58, y + 5), radius=3, fill=(160, 188, 228, 255))
    return img


def degree_certificate(size: tuple[int, int]) -> Image.Image:
    img = rounded_panel(size, "#F4FAFF", "#C6D8F4")
    draw = ImageDraw.Draw(img)
    w, h = size
    font = load_font(22)
    draw.text((30, 24), "学位证书", fill=(138, 166, 210, 255), font=font)
    draw.ellipse((24, 22, 64, 62), outline=(220, 152, 165, 220), width=3)
    draw.line((30, 52, 58, 34), fill=(220, 152, 165, 180), width=2)
    draw.line((28, 34, 60, 52), fill=(220, 152, 165, 180), width=2)
    for idx, y in enumerate((48, 72, 96)):
        extra = 56 if idx == 0 else 80
        draw.rounded_rectangle((90, y, w - extra, y + 5), radius=3, fill=(160, 188, 228, 255))
    draw.rounded_rectangle((34, 126, w - 40, 132), radius=3, fill=(160, 188, 228, 255))
    draw.rounded_rectangle((34, 152, w - 82, 158), radius=3, fill=(160, 188, 228, 255))
    draw.ellipse((w - 110, h - 100, w - 74, h - 64), fill=(80, 99, 130, 255))
    draw.ellipse((w - 104, h - 84, w - 78, h - 40), fill=(249, 214, 190, 255))
    draw.rounded_rectangle((w - 118, h - 42, w - 64, h - 14), radius=12, fill=(204, 226, 250, 255))
    return img


ASSETS: list[tuple[str, tuple[int, int], Callable[[tuple[int, int]], Image.Image]]] = [
    ("id_doc_identity_card.webp", (203, 140), identity_card),
    ("id_doc_bank_card.webp", (203, 140), bank_card),
    ("id_doc_household_register.webp", (318, 242), household_register),
    ("id_doc_invoice.webp", (512, 312), invoice),
    ("id_doc_passport.webp", (282, 384), passport),
    ("id_doc_driver_license.webp", (412, 164), driver_license),
    ("id_doc_vehicle_license.webp", (452, 240), vehicle_license),
    ("id_doc_property_certificate.webp", (472, 332), property_certificate),
    ("id_doc_business_license.webp", (626, 452), business_license),
    ("id_doc_diploma.webp", (502, 352), diploma),
    ("id_doc_degree_certificate.webp", (502, 352), degree_certificate),
]


def build_preview_sheet(rendered: list[tuple[str, Image.Image]]) -> None:
    labels = {
        "id_doc_identity_card.webp": "身份证",
        "id_doc_bank_card.webp": "银行卡",
        "id_doc_household_register.webp": "户口本",
        "id_doc_invoice.webp": "发票",
        "id_doc_passport.webp": "护照",
        "id_doc_driver_license.webp": "驾照",
        "id_doc_vehicle_license.webp": "行驶证",
        "id_doc_property_certificate.webp": "房产证",
        "id_doc_business_license.webp": "营业执照",
        "id_doc_diploma.webp": "毕业证",
        "id_doc_degree_certificate.webp": "学位证",
    }
    cols = 4
    cell_w, cell_h = 280, 240
    rows = (len(rendered) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (14, 17, 24, 255))
    draw = ImageDraw.Draw(sheet)
    font = load_font(18)

    for index, (name, img) in enumerate(rendered):
        col = index % cols
        row = index // cols
        ox = col * cell_w
        oy = row * cell_h
        thumb = img.copy()
        thumb.thumbnail((210, 140))
        px = ox + (cell_w - thumb.width) // 2
        py = oy + 28
        sheet.alpha_composite(thumb, (px, py))
        text = labels.get(name, name)
        text_box = draw.textbbox((0, 0), text, font=font)
        tw = text_box[2] - text_box[0]
        draw.text((ox + (cell_w - tw) // 2, oy + 184), text, fill=(255, 255, 255, 255), font=font)

    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW_PATH)


def main() -> None:
    rendered: list[tuple[str, Image.Image]] = []
    for filename, size, builder in ASSETS:
        image = builder(size)
        output = OUT_DIR / filename
        image.save(output, format="WEBP", quality=95, method=6)
        rendered.append((filename, image))
        print(f"wrote {output}")
    build_preview_sheet(rendered)
    print(f"wrote {PREVIEW_PATH}")


if __name__ == "__main__":
    main()
