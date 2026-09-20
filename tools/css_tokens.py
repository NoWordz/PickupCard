#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
design/tokens.css  ->  assets/pickupcard/styles/*.json

【为什么要有这个脚本】在这之前，CSS 草稿和 Java 实现是"两份独立的真相"：
浏览器里调好的数，要人手抄回 default.json，抄错、抄漏、改了这边忘那边，
全都没有任何东西会报错。实测过一次代价：草稿和游戏里差了"竖条该不该上下内缩"、
"该左对齐还是右对齐"两条结构差异，而当时两边没有任何可比对的依据。

这个脚本把"手抄"换成"编译"：tokens.css 的 :root 是唯一真源，
HTML 直接引它、Java 读它生成的 JSON，两边不可能再漂。

【清单外的键一律报错退出，不静默忽略】静默失败是最毒的一种：上一版用 ApricityUI 时
`radial-gradient` 不解析也不报错，改十版设计都不知道为什么画面没变。
所以这里的规则是：遇到没登记过的自定义属性 -> 打印名字 + 行号 -> exit 1。

用法：
    python tools/css_tokens.py            # 生成
    python tools/css_tokens.py --check    # 只校验不写盘（CI 用）
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "tokens.css"
OUT_DIR = ROOT / "platforms" / "1.20.1-forge" / "src" / "main" / "resources" / "assets" / "pickupcard" / "styles"

# 自定义属性 -> (JSON 段, JSON 键, 类型)
# 没登记在这里的名字一律视为错误。加新参数时必须同时改这里，这正是想要的手续。
SCHEMA = {
    # 几何
    "--pc-radius":          ("geometry", "cornerRadius", "int"),
    "--pc-pad-h":           ("geometry", "paddingH", "int"),
    "--pc-pad-v":           ("geometry", "paddingV", "int"),
    "--pc-gap":             ("geometry", "gap", "int"),
    "--pc-icon":            ("geometry", "iconSize", "int"),
    "--pc-bar-w":           ("geometry", "barWidth", "int"),
    "--pc-bar-inset-y":     ("geometry", "barInsetY", "int"),
    "--pc-border-w":        ("geometry", "borderWidth", "int"),
    # 材质
    "--pc-fill-top":        ("material", "fillTop", "color"),
    "--pc-fill-bottom":     ("material", "fillBottom", "color"),
    "--pc-border":          ("material", "border", "color"),
    "--pc-glow-alpha":      ("material", "glowAlpha", "int"),
    "--pc-shimmer-alpha":   ("material", "shimmerAlpha", "int"),
    # 文字
    "--pc-name":            ("text", "nameColor", "color"),
    # 强调色
    "--pc-accent-common":   ("accent", "common", "color"),
    "--pc-accent-uncommon": ("accent", "uncommon", "color"),
    "--pc-accent-rare":     ("accent", "rare", "color"),
    "--pc-accent-epic":     ("accent", "epic", "color"),
    "--pc-accent-xp":       ("accent", "xp", "color"),
    "--pc-accent-overflow": ("accent", "overflow", "color"),
    # 动画
    "--pc-enter-ms":        ("animation", "enterMs", "int"),
    "--pc-bump-ms":         ("animation", "bumpMs", "int"),
    "--pc-revive-ms":       ("animation", "reviveMs", "int"),
    "--pc-bump-peak":       ("animation", "bumpPeakPercent", "int"),
    "--pc-enter-enabled":   ("animation", "enterEnabled", "bool"),
    "--pc-bump-enabled":    ("animation", "bumpEnabled", "bool"),
    "--pc-glow-pulse":      ("animation", "glowPulseEnabled", "bool"),
}

# 哪些选择器的块会被读取
PRESETS = {
    None: "default.json",                       # 深色玻璃（默认）
    'data-theme="light"': "light.json",         # 浅色
}


class TokenError(Exception):
    pass


def strip_comments(text: str) -> str:
    """去掉 /* ... */，但保留行号（用等长空白替换）。"""
    def repl(m):
        return re.sub(r"[^\n]", " ", m.group(0))
    return re.sub(r"/\*.*?\*/", repl, text, flags=re.S)


def blocks(text: str):
    """产出 (选择器, 块内容, 块起始行号)。"""
    for m in re.finditer(r"([^{}]*)\{([^{}]*)\}", text):
        selector = m.group(1).strip()
        body = m.group(2)
        line = text[: m.start(2)].count("\n") + 1
        yield selector, body, line


def parse_preset(text: str, theme: str):
    """收集某个预设块里的自定义属性。theme=None 表示 :root（默认）。"""
    found = {}
    wanted = ":root" if theme is None else f':root[{theme}]'
    for selector, body, line in blocks(text):
        # ":root, :root[data-theme=\"dark\"]" 这种并列写法也接受
        parts = [p.strip() for p in selector.split(",")]
        if wanted not in parts:
            continue
        for i, raw in enumerate(body.splitlines()):
            entry = raw.strip()
            if not entry or ":" not in entry:
                continue
            name, _, value = entry.partition(":")
            name = name.strip()
            value = value.strip().rstrip(";").strip()
            if not name.startswith("--"):
                continue
            if name not in SCHEMA:
                raise TokenError(
                    f"{SOURCE.name}:{line + i}  未登记的自定义属性 '{name}'\n"
                    f"    如果这是新参数，请同时把它加进 tools/css_tokens.py 的 SCHEMA，\n"
                    f"    否则 Java 侧永远读不到它 —— 那正是本脚本要防的静默失败。"
                )
            found[name] = value
    return found


def convert(name: str, value: str, kind: str):
    if kind == "int":
        m = re.fullmatch(r"(-?\d+(?:\.\d+)?)(?:px)?", value)
        if not m:
            raise TokenError(f"'{name}' 期望像素值或整数，实际是 '{value}'")
        return int(round(float(m.group(1))))
    if kind == "bool":
        if value not in ("0", "1"):
            raise TokenError(f"'{name}' 期望 0 或 1，实际是 '{value}'")
        return value == "1"
    if kind == "color":
        s = value.lstrip("#")
        if not re.fullmatch(r"[0-9a-fA-F]{6}([0-9a-fA-F]{2})?", s):
            raise TokenError(f"'{name}' 期望 #RRGGBB 或 #RRGGBBAA，实际是 '{value}'")
        return "#" + s.upper()
    raise TokenError(f"未知类型 {kind}")


def build(text: str, theme: str):
    raw = parse_preset(text, theme)
    out = {"_comment": "由 tools/css_tokens.py 从 design/tokens.css 生成，不要手改。"
                       f"{'' if theme is None else ' 预设：浅色。'}"}
    missing = []
    for name, (section, key, kind) in SCHEMA.items():
        if name not in raw:
            # 浅色预设允许只覆盖一部分：缺的键由默认预设兜底，不算错
            if theme is not None:
                continue
            missing.append(name)
            continue
        out.setdefault(section, {})[key] = convert(name, raw[name], kind)
    if missing:
        raise TokenError("默认预设里缺少这些键：\n    " + "\n    ".join(missing))
    if theme is not None:
        base = build(text, None)
        for section, values in out.items():
            if section == "_comment":
                continue
            merged = dict(base.get(section, {}))
            merged.update(values)
            out[section] = merged
    return out


def main() -> int:
    check_only = "--check" in sys.argv
    if not SOURCE.exists():
        print(f"找不到 {SOURCE}", file=sys.stderr)
        return 1
    text = strip_comments(SOURCE.read_text(encoding="utf-8"))
    try:
        results = {name: build(text, theme) for theme, name in PRESETS.items()}
    except TokenError as e:
        print(f"css_tokens: 失败\n  {e}", file=sys.stderr)
        return 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for filename, data in results.items():
        target = OUT_DIR / filename
        payload = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
        if check_only:
            if not target.exists() or target.read_text(encoding="utf-8") != payload:
                print(f"css_tokens: {target.name} 与 tokens.css 不同步（跑一次不带 --check 的即可）",
                      file=sys.stderr)
                return 1
        else:
            target.write_text(payload, encoding="utf-8", newline="\n")
        print(f"  {filename:<14} {len(json.dumps(data))} 字节")
    print(f"css_tokens: {'校验通过' if check_only else '已生成'} {len(results)} 份主题")
    return 0


if __name__ == "__main__":
    sys.exit(main())
