# -*- coding: utf-8 -*-
"""第二轮首页视觉精修：处理三张新设计素材并生成工程资源。

输入（design_image/）：
- 应用纯图案logo.png      纯图案萤火徽标（透明底）→ 裁边后同时用于应用内 Logo 与桌面图标
- 首页上方背景.png        顶部文档+萤火轨道插画（透明底）→ home_top_decor
- 主卡片背景.png          主卡片微光背景（白底圆角矩形图）→ home_scan_card_bg（白底转透明）

输出：
- drawable-nodpi/firefly_logo_badge.png
- drawable-nodpi/ic_launcher_foreground_art.png（前景主体放大）
- drawable-nodpi/ic_launcher_background_art.png / ic_launcher_foreground_mono.png
- mipmap-*/ic_launcher(.round).webp
- drawable-nodpi/home_top_decor.webp
- drawable-nodpi/home_scan_card_bg.webp
"""
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "design_image")
RES = os.path.join(ROOT, "app", "src", "main", "res")

LOGO_SRC = os.path.join(SRC_DIR, "应用纯图案logo.png")
TOP_BG_SRC = os.path.join(SRC_DIR, "首页上方背景.png")
CARD_BG_SRC = os.path.join(SRC_DIR, "主卡片背景.png")

MIST = (246, 250, 249, 255)
ICON_FOREGROUND_SCALE = 0.76  # 主体更大，缩小无意义留白


def trim_alpha(im: Image.Image, pad_ratio: float = 0.0) -> Image.Image:
    bbox = im.getchannel("A").getbbox()
    if bbox is None:
        raise RuntimeError("透明图无内容")
    im = im.crop(bbox)
    if pad_ratio > 0:
        pw = int(im.width * pad_ratio)
        canvas = Image.new("RGBA", (im.width + pw * 2, im.height + pw * 2), (0, 0, 0, 0))
        canvas.paste(im, (pw, pw), im)
        return canvas
    return im


def square_canvas(im: Image.Image) -> Image.Image:
    side = max(im.width, im.height)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(im, ((side - im.width) // 2, (side - im.height) // 2), im)
    return canvas


def paste_center(base: Image.Image, fg: Image.Image, scale: float) -> None:
    size = int(base.width * scale)
    fg_r = fg.resize((size, size), Image.LANCZOS)
    base.paste(fg_r, ((base.width - size) // 2, (base.height - size) // 2), fg_r)


def make_monochrome() -> Image.Image:
    """单色剪影：圆环 + 三枚翅片 + 中心光点 + 轨道弧。"""
    import math

    S = 432
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    white = (255, 255, 255, 255)
    cx = cy = S // 2
    R = int(S * 0.34)
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=white, width=int(S * 0.034))
    for ang in (-90, 30, 150):
        wl, wh = int(S * 0.30), int(S * 0.13)
        wing = Image.new("RGBA", (wl * 2, wh * 2), (0, 0, 0, 0))
        wd = ImageDraw.Draw(wing)
        wd.ellipse([0, 0, wl * 2 - 1, wh * 2 - 1], fill=white)
        wing = wing.rotate(ang, expand=True, resample=Image.BICUBIC)
        rad = math.radians(ang)
        ox = int(cx + math.cos(rad) * S * 0.10 - wing.width / 2)
        oy = int(cy + math.sin(rad) * S * 0.10 - wing.height / 2)
        im.paste(wing, (ox, oy), wing)
    r2 = int(S * 0.085)
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=white)
    return im


def white_to_alpha(im: Image.Image) -> Image.Image:
    """白底转透明：只剔除与图片边缘连通的背景白，保留图案内部的白色发光。

    直接用亮度转透明会把萤火光晕里的白色高光一并抠掉（上一轮的问题），
    因此用洪泛填充（flood fill）区分“外部背景白”和“图案内部白”。
    """
    import scipy.ndimage as ndi

    rgb = np.asarray(im.convert("RGB")).astype(np.float32)
    h, w = rgb.shape[:2]
    # 近白像素（背景候选）
    near_white = rgb.min(axis=2) > 238
    # 从图像边缘出发，只标记与边缘连通的白 → 外部背景
    exterior_seed = np.zeros((h, w), dtype=bool)
    exterior_seed[0, :] = near_white[0, :]
    exterior_seed[-1, :] = near_white[-1, :]
    exterior_seed[:, 0] = near_white[:, 0]
    exterior_seed[:, -1] = near_white[:, -1]
    exterior = ndi.binary_propagation(exterior_seed, mask=near_white)
    # 轻微羽化边缘，避免锯齿
    exterior_soft = ndi.gaussian_filter(exterior.astype(np.float32), sigma=1.6)
    alpha = np.clip((1.0 - exterior_soft) * 255.0, 0, 255)

    a = alpha / 255.0
    out = np.zeros((h, w, 4), dtype=np.uint8)
    safe = np.maximum(a, 1e-3)
    # 仅对半透明边界做颜色去污染；内部像素保持原色（含白色高光）
    for c in range(3):
        decontam = (rgb[:, :, c] - 255.0 * (1.0 - a)) / safe
        solid = rgb[:, :, c]
        blend = np.clip(np.where(a > 0.995, solid, decontam), 0, 255)
        out[:, :, c] = blend.astype(np.uint8)
    out[:, :, 3] = alpha.astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def main() -> None:
    # 1) Logo：裁掉透明留白 → 应用内资源 + 图标前景
    logo = trim_alpha(Image.open(LOGO_SRC).convert("RGBA"), pad_ratio=0.03)
    logo = square_canvas(logo).resize((1024, 1024), Image.LANCZOS)
    logo.resize((512, 512), Image.LANCZOS).save(
        os.path.join(RES, "drawable-nodpi", "firefly_logo_badge.png"), optimize=True
    )

    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    paste_center(fg, logo, ICON_FOREGROUND_SCALE)
    fg.save(os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground_art.png"), optimize=True)
    Image.new("RGBA", (432, 432), MIST).save(
        os.path.join(RES, "drawable-nodpi", "ic_launcher_background_art.png"), optimize=True
    )
    make_monochrome().save(
        os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground_mono.png"), optimize=True
    )

    composed = Image.new("RGBA", (432, 432), MIST)
    paste_center(composed, logo, 0.86)
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, px in sizes.items():
        icon = composed.resize((px, px), Image.LANCZOS)
        icon.convert("RGB").save(
            os.path.join(RES, f"mipmap-{dpi}", "ic_launcher.webp"), "WEBP", quality=95
        )
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px, px], fill=255)
        round_icon = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        round_icon.paste(icon, (0, 0), mask)
        round_icon.save(
            os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_round.webp"), "WEBP", quality=95
        )

    # 2) 首页上方背景插画（透明底，仅裁边 + 控制体积）
    top = trim_alpha(Image.open(TOP_BG_SRC).convert("RGBA"))
    if top.width > 900:
        ratio = 900 / top.width
        top = top.resize((900, int(top.height * ratio)), Image.LANCZOS)
    top.save(os.path.join(RES, "drawable-nodpi", "home_top_decor.webp"), "WEBP", quality=92)

    # 3) 主卡片背景（白底 → 透明）
    card = white_to_alpha(Image.open(CARD_BG_SRC))
    card = trim_alpha(card, pad_ratio=0.01)
    # 只保留完全不透明的内区：Compose 侧用 ContentScale.Crop 全幅铺满卡片，
    # 图案边缘不再羽化，避免底色从边缘透出的“深绿边”
    solid_bbox = card.getchannel("A").point(lambda a: 255 if a >= 250 else 0).getbbox()
    if solid_bbox is not None:
        inset = 4
        l = min(solid_bbox[0] + inset, card.width // 2)
        t = min(solid_bbox[1] + inset, card.height // 2)
        r = max(solid_bbox[2] - inset, card.width // 2 + 1)
        b = max(solid_bbox[3] - inset, card.height // 2 + 1)
        card = card.crop((l, t, r, b)).convert("RGB")
    if card.width > 1100:
        ratio = 1100 / card.width
        card = card.resize((1100, int(card.height * ratio)), Image.LANCZOS)
    card.save(os.path.join(RES, "drawable-nodpi", "home_scan_card_bg.webp"), "WEBP", quality=92)

    print("home visual pass-2 assets generated")


if __name__ == "__main__":
    main()
