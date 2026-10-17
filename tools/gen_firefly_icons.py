# -*- coding: utf-8 -*-
"""从萤火Scan 徽标母版生成应用图标与内嵌 Logo 资源。

输出：
- drawable-nodpi/firefly_logo_badge.png      透明底圆形徽章（应用内品牌位）
- drawable-nodpi/ic_launcher_foreground_art.png  Adaptive Icon 前景
- drawable-nodpi/ic_launcher_background_art.png  Adaptive Icon 背景（雾白）
- drawable-nodpi/ic_launcher_foreground_mono.png 单色（主题图标）剪影
- mipmap-*/ic_launcher(.round).webp          旧密度兜底图标
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "design_image", "萤火Scan 星光徽标_透明背景.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

MIST = (246, 250, 249, 255)      # Background 雾白 #F6FAF9
DEEP_TEAL = (24, 90, 92, 255)    # 徽章深青（取自母版视觉）
MINT = (145, 220, 203, 255)      # Mint #91DCCB
WARM_GLOW = (255, 244, 200, 255)


def load_badge() -> Image.Image:
    """取透明底徽标的圆形徽章区域（图片上方，与文字之间存在空白行带）。"""
    import numpy as np

    im = Image.open(SRC).convert("RGBA")
    w, h = im.size
    rows = (np.asarray(im.getchannel("A")) > 24).sum(axis=1)
    # 从 1/4 高度处向下找第一条明显空白行带（徽章与文字的分隔）
    cut = int(h * 0.68)
    run = 0
    for y in range(h // 4, h):
        if rows[y] == 0:
            run += 1
            if run >= 8:
                cut = y - run + 1
                break
        else:
            run = 0
    top = im.crop((0, 0, w, cut))
    bbox = top.getchannel("A").getbbox()
    if bbox is None:
        raise RuntimeError("未找到徽章像素")
    # 轨道光环向右伸出，包围盒可能略宽；以高度为边长取正方形并居中
    # 注意：必须在已截断的 top 上裁剪，避免向下扩进文字区域
    bw = bbox[2] - bbox[0]
    bh = bbox[3] - bbox[1]
    side = max(bw, bh)
    cx = (bbox[0] + bbox[2]) // 2
    cy = (bbox[1] + bbox[3]) // 2
    half = side // 2 + int(side * 0.02)
    left = max(0, cx - half)
    upper = max(0, cy - half)
    right = min(w, cx + half)
    lower = min(top.height, cy + half)
    badge = top.crop((left, upper, right, lower))
    # 归一化为正方形画布
    side_px = max(badge.size)
    canvas = Image.new("RGBA", (side_px, side_px), (0, 0, 0, 0))
    canvas.paste(badge, ((side_px - badge.width) // 2, (side_px - badge.height) // 2), badge)
    return canvas.resize((1024, 1024), Image.LANCZOS)


def paste_center(base: Image.Image, fg: Image.Image, scale: float) -> None:
    size = int(base.width * scale)
    fg_r = fg.resize((size, size), Image.LANCZOS)
    base.paste(fg_r, ((base.width - size) // 2, (base.height - size) // 2), fg_r)


def make_monochrome() -> Image.Image:
    """程序化绘制单色剪影：圆环 + 三枚翅片 + 中心光点 + 轨道弧。"""
    S = 432
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    white = (255, 255, 255, 255)
    cx = cy = S // 2
    R = int(S * 0.33)
    # 圆环
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=white, width=int(S * 0.035))
    # 翅片（三枚旋转椭圆）
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
    # 中心光点
    r2 = int(S * 0.085)
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=white)
    return im


def main() -> None:
    badge = load_badge()

    # 1) 应用内品牌徽章
    badge.resize((512, 512), Image.LANCZOS).save(
        os.path.join(RES, "drawable-nodpi", "firefly_logo_badge.png"), optimize=True
    )

    # 2) Adaptive 前景：432 画布，徽章约占 68%（安全区内）
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    paste_center(fg, badge, 0.68)
    fg.save(os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground_art.png"), optimize=True)

    # 3) Adaptive 背景：雾白纯色
    Image.new("RGBA", (432, 432), MIST).save(
        os.path.join(RES, "drawable-nodpi", "ic_launcher_background_art.png"), optimize=True
    )

    # 4) 单色剪影
    make_monochrome().save(
        os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground_mono.png"), optimize=True
    )

    # 5) 旧密度兜底（minSdk 26 用不到，但保持资源一致）
    composed = Image.new("RGBA", (432, 432), MIST)
    paste_center(composed, badge, 0.80)
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, px in sizes.items():
        icon = composed.resize((px, px), Image.LANCZOS)
        # 方形
        icon.convert("RGB").save(
            os.path.join(RES, f"mipmap-{dpi}", "ic_launcher.webp"), "WEBP", quality=95
        )
        # 圆形
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px, px], fill=255)
        round_icon = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        round_icon.paste(icon, (0, 0), mask)
        round_icon.save(
            os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_round.webp"), "WEBP", quality=95
        )

    print("icons generated")


if __name__ == "__main__":
    main()
