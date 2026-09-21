#!/usr/bin/env python3
"""从 `design/logo.png` 派生 mod 图标：`design/icon.png`（128×128）。

【为什么还是脚本】图标是唯一一处没有门禁盯着的视觉资产 —— 只要"重来一次"这件事不可靠，
它就一定会漂，而且漂了没人看得出来。脚本保证一条命令重出，产物再交给
`tools/verify_jars.py` 与 jar 里的 `logo.png` 逐字节对照。

【为什么不再读 tokens.css】2026-09-21 起图标的**正本**是 `design/logo.png`
（手工画的那张：卡片堆 + 镐子），它不随主题配色变。这是有意的取舍：主题是给卡面的，
项目 logo 要的是"一眼认得出"。所以这个脚本只剩两件事 —— **裁到主体**、**缩到 128**。

【为什么要裁】原图四周留白很大，直接缩到 128 之后主体只占中间一小块；mod 列表里常见的
显示尺寸是 32px，那一档上就只剩一团绿。裁到主体（含微光）再缩，同样 128 像素里主体大一圈。

用法：
    python design/icon.py                  # 出 design/icon.png（128×128）
    python design/icon.py --size 256       # 更大的那份（不入库）
    python design/icon.py --print-box      # 只打印裁切框，不写盘
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "logo.png"

# 主体与背景的判定阈值（单通道最大差值）。原图背景是带噪的深色，主体是亮绿描边 + 微光；
# 12 这一档能把微光算进主体（不然裁完描边贴边、看着像被切了），又不会把背景噪点吃进来。
CONTENT_THRESHOLD = 12

# 裁切框外侧补的留白，按主体长边比例算 —— 太紧会让描边顶到画布边
PADDING_RATIO = 0.07


def background_color(img: Image.Image) -> tuple[int, int, int]:
    """背景色取四条边框像素的中位数，比取单个角点稳（角上有噪或水印时会骗人）。"""
    w, h = img.size
    samples = []
    for x in range(w):
        samples.append(img.getpixel((x, 1)))
        samples.append(img.getpixel((x, h - 2)))
    for y in range(h):
        samples.append(img.getpixel((1, y)))
        samples.append(img.getpixel((w - 2, y)))
    channels = list(zip(*samples))
    return tuple(sorted(c)[len(c) // 2] for c in channels)  # type: ignore[return-value]


def content_box(img: Image.Image, bg: tuple[int, int, int]) -> tuple[int, int, int, int]:
    """主体（含微光）的外接矩形。逐像素比背景，取最大通道差。"""
    w, h = img.size
    pixels = img.load()
    min_x, min_y, max_x, max_y = w, h, -1, -1
    for y in range(h):
        for x in range(w):
            r, g, b = pixels[x, y]  # type: ignore[misc]
            diff = max(abs(r - bg[0]), abs(g - bg[1]), abs(b - bg[2]))
            if diff > CONTENT_THRESHOLD:
                if x < min_x:
                    min_x = x
                if y < min_y:
                    min_y = y
                if x > max_x:
                    max_x = x
                if y > max_y:
                    max_y = y
    if max_x < 0:
        raise SystemExit(f"{SOURCE.name} 里没找到与背景不同的像素 —— 阈值或图都不对")
    return min_x, min_y, max_x + 1, max_y + 1


def build(source: Path, size: int) -> Image.Image:
    img = Image.open(source).convert("RGB")
    bg = background_color(img)
    x0, y0, x1, y1 = content_box(img, bg)
    content_w, content_h = x1 - x0, y1 - y0
    pad = round(max(content_w, content_h) * PADDING_RATIO)
    side = max(content_w, content_h) + 2 * pad

    # 正方形画布填背景色，主体居中贴进去 —— 主体是横的（卡是横的），上下留出来的是背景，
    # 缩到 128 之后看起来就是"卡片居中"，而不是被拉扁。
    canvas = Image.new("RGB", (side, side), bg)
    canvas.paste(img.crop((x0, y0, x1, y1)),
                 ((side - content_w) // 2, (side - content_h) // 2))
    return canvas.resize((size, size), Image.LANCZOS)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--size", type=int, default=128)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--out", type=Path, default=ROOT / "design" / "icon.png")
    parser.add_argument("--print-box", action="store_true",
                        help="只打印裁切框（调试用），不写盘")
    args = parser.parse_args()

    if not args.source.is_file():
        raise SystemExit(f"找不到图标正本 {args.source} —— 图标不该由脚本自己编一张")
    if args.print_box:
        img = Image.open(args.source).convert("RGB")
        bg = background_color(img)
        print(f"{args.source.name} {img.size} 背景 {bg} 主体框 {content_box(img, bg)}")
        return 0

    icon = build(args.source, args.size)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(args.out, "PNG", optimize=True)
    print(f"icon -> {args.out}（{args.size}×{args.size}，正本 {args.source.name}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
