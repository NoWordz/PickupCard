#!/usr/bin/env python3
"""生成 mod 图标：把卡面的三段式按 token 画成一张 128×128 的 PNG。

【为什么是脚本，而不是一张画好的 PNG】配色与几何全部从 `design/tokens.css` 读 —— 改主题
之后重跑一次，图标就跟着变；手画的那张不会，而它漂了没人看得出来（图标是唯一一处
"没有门禁盯着"的视觉资产）。

用法：
    python design/icon.py                    # 出 design/icon.png（128×128）
    python design/icon.py --size 256         # 更大的那份（商店用）
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "design" / "tokens.css"

# 图标里用哪一档强调色：epic 紫在深底上最抓眼，也正好是"高稀有 + 有微光"的那一档
ACCENT_TOKEN = "accent-epic"


def token(name: str) -> str:
    text = TOKENS.read_text(encoding="utf-8")
    match = re.search(rf"--pc-{re.escape(name)}:\s*([^;]+);", text)
    if not match:
        raise SystemExit(f"tokens.css 里没有 --pc-{name} —— 图标不该自己编一个颜色")
    return match.group(1).strip()


def rgba(value: str) -> tuple[int, int, int, int]:
    v = value.lstrip("#")
    r, g, b = int(v[0:2], 16), int(v[2:4], 16), int(v[4:6], 16)
    a = int(v[6:8], 16) if len(v) >= 8 else 255
    return r, g, b, a


def scale(value: str) -> float:
    return float(value.replace("px", "").strip())


def lerp(a: int, b: int, t: float) -> int:
    return int(round(a + (b - a) * t))


def rounded_row_inset(dy: int, radius: float) -> int:
    """圆角矩形里，距上下边缘 dy 的那一行要往里缩多少像素。"""
    if dy >= radius:
        return 0
    dx = radius - (radius ** 2 - (radius - dy - 0.5) ** 2) ** 0.5
    return int(round(dx))


def draw_card(layer: Image.Image, box: tuple[float, float, float, float], radius: float,
              top: tuple[int, int, int, int], bottom: tuple[int, int, int, int],
              border: tuple[int, int, int, int], dim: float = 1.0) -> None:
    """一张卡：渐变底 + 1px 描边。dim < 1 用来画压在后面的旧卡。"""
    draw = ImageDraw.Draw(layer)
    x0, y0, x1, y1 = (int(round(v)) for v in box)

    # 描边先画：外圈一整块描边色，随后被内缩 1px 的渐变盖住中间，露出来的就是 1px 边
    for y in range(y0, y1):
        dy = min(y - y0, y1 - 1 - y)
        inset = rounded_row_inset(dy, radius)
        draw.line([(x0 + inset, y), (x1 - 1 - inset, y)],
                  fill=(border[0], border[1], border[2], int(border[3] * dim)))

    ix0, iy0, ix1, iy1 = x0 + 1, y0 + 1, x1 - 1, y1 - 1
    height = max(1, iy1 - iy0 - 1)
    inner_radius = max(0.0, radius - 1)
    for y in range(iy0, iy1):
        t = (y - iy0) / height
        color = (lerp(top[0], bottom[0], t), lerp(top[1], bottom[1], t), lerp(top[2], bottom[2], t))
        alpha = lerp(top[3], bottom[3], t)
        dy = min(y - iy0, iy1 - 1 - y)
        inset = rounded_row_inset(dy, inner_radius)
        draw.line([(ix0 + inset, y), (ix1 - 1 - inset, y)],
                  fill=color + (int(alpha * dim),))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--size", type=int, default=128)
    parser.add_argument("--out", type=Path, default=ROOT / "design" / "icon.png")
    args = parser.parse_args()

    size = args.size
    unit = size / 128.0                      # 一切都按 128 设计，按需放大

    fill_top, fill_bottom = rgba(token("fill-top")), rgba(token("fill-bottom"))
    border = rgba(token("border"))
    accent = rgba(token(ACCENT_TOKEN))
    name = rgba(token("name"))
    radius = scale(token("radius")) * 2.4 * unit

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # 背景：竖直渐变，比卡面更暗一档 —— 图标要能在浅色主题的列表里也看得出边界
    bg = Image.new("RGBA", (size, size))
    bgd = ImageDraw.Draw(bg)
    for y in range(size):
        t = y / max(1, size - 1)
        bgd.line([(0, y), (size, y)],
                 fill=(lerp(0x11, 0x1C, t), lerp(0x15, 0x22, t), lerp(0x1C, 0x2C, t), 255))
    img = Image.alpha_composite(img, bg)

    def box(cx: float, cy: float, w: float, h: float) -> tuple[float, float, float, float]:
        return ((cx - w / 2) * unit, (cy - h / 2) * unit,
                (cx + w / 2) * unit, (cy + h / 2) * unit)

    # 后面两张旧卡：越往上越窄、越暗（真实的堆叠是"新的在下面，旧的被顶上去"）
    stack = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_card(stack, box(64, 42, 84, 30), radius * 0.9, fill_top, fill_bottom, border, dim=0.35)
    draw_card(stack, box(64, 56, 94, 32), radius * 0.95, fill_top, fill_bottom, border, dim=0.55)

    # 微光：主卡外侧一圈柔和的强调色，对应稀有卡的 glow
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gx0, gy0, gx1, gy1 = box(64, 82, 108, 40)
    gd.rounded_rectangle([gx0, gy0, gx1, gy1], radius=radius + 2 * unit,
                         fill=accent[:3] + (70,))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=5 * unit))
    img = Image.alpha_composite(img, glow)
    img = Image.alpha_composite(img, stack)

    # 主卡
    main = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mx0, my0, mx1, my1 = box(64, 82, 104, 36)
    draw_card(main, (mx0, my0, mx1, my1), radius, fill_top, fill_bottom, border)
    d = ImageDraw.Draw(main)

    card_h = my1 - my0
    inset_y = card_h * 0.13
    bar_x = mx0 + 7 * unit
    bar_w = 5 * unit
    d.rounded_rectangle([bar_x, my0 + inset_y, bar_x + bar_w, my1 - inset_y],
                        radius=bar_w / 2, fill=accent)

    icon_x = bar_x + bar_w + 6 * unit
    icon_s = 15 * unit
    icon_y = (my0 + my1) / 2 - icon_s / 2
    d.rounded_rectangle([icon_x, icon_y, icon_x + icon_s, icon_y + icon_s],
                        radius=3 * unit, fill=name[:3] + (70,))
    d.rounded_rectangle([icon_x + 3.5 * unit, icon_y + 3.5 * unit,
                         icon_x + icon_s - 3.5 * unit, icon_y + icon_s - 3.5 * unit],
                        radius=1.5 * unit, fill=name[:3] + (150,))

    # 名字只用**一条**线：缩到 32px（mod 列表里的真实尺寸）时，两条线的间隙会糊在一起，
    # 读起来像一条脏边而不是两行字；数量那块紫色已经足够说明"这里还有个数字"。
    text_x = icon_x + icon_s + 6 * unit
    line_h = 4.0 * unit
    d.rounded_rectangle([text_x, (my0 + my1) / 2 - line_h / 2,
                         text_x + 38 * unit, (my0 + my1) / 2 + line_h / 2],
                        radius=line_h / 2, fill=name[:3] + (235,))

    count_w = 13 * unit
    d.rounded_rectangle([mx1 - 8 * unit - count_w, (my0 + my1) / 2 - 4.5 * unit,
                         mx1 - 8 * unit, (my0 + my1) / 2 + 4.5 * unit],
                        radius=2 * unit, fill=accent)
    img = Image.alpha_composite(img, main)

    img.convert("RGB").save(args.out, "PNG")
    print(f"icon -> {args.out}（{size}×{size}，强调色 {token(ACCENT_TOKEN)}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
