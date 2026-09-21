#!/usr/bin/env python3
"""把两份商店正文正本渲染成三份产物。

【为什么要有这个脚本】同一份内容要在三个平台上出现（Modrinth / CurseForge / MC 百科），
差异只有两处：**语言**与**标记**（`## X` ↔ `[h1=X]`、Markdown 表格 ↔ 百科的 `格| 格；`、
要不要徽章行与头图、条目之间空不空行）。手写三份 = 手同步三遍；2026-09-21 用户的原话是
「好乱啊怎么办」—— 那一天仓库里同时躺着四份描述文件、两种标记、两种语言，闸一口气报了 15 条不一致。

现在的分工：

    docs/store/zh.md   ← 正本（中文，纯 markdown，用户微调的就是它）
    docs/store/en.md   ← 正本（英文，同一个节表，逐节对应）
        │  python tools/store_copy.py --write
        ├─→ docs/store/modrinth.md    英文 + 头图 + 徽章行
        ├─→ docs/store/curseforge.md  与上面逐字节相同
        └─→ docs/store/mcmod.md       中文 + [mark:title_menu] + [h1=]，无徽章无头图

**结构以桌面《商店格式规范》为准**（用户 2026-09-21 提交）：
一句话简介 → 概述 → 功能 → 兼容性（表格）→ 配置（表格）→ 已知限制 → FAQ → 问题反馈。

**正本里不写的东西**（都由这里生成，写了就会被 --check 判为漂）：头图那一行、`# 名字`、
徽章行（MC / 加载器 / 侧别 / Java / 版本 / 许可，全部从仓库身份与目标声明里读出来）、
`[mark:title_menu]`。**一句话简介写在正本第一段**（它要分语言，不能从英文的 mod_description 派生）。

用法：
    python tools/store_copy.py --write    # 重出三份产物
    python tools/store_copy.py --check    # 只校验（产物与正本不一致就退出 1）
退出码：0 = 一致；1 = 有差异或读不到身份。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "docs" / "store"

SOURCES = {"zh": STORE / "zh.md", "en": STORE / "en.md"}

# 产物：源语言 + 标记风格。英文两份互为副本（CurseForge 只能人工贴，贴的就是同一份）。
OUTPUTS = {
    "modrinth.md": {"source": "en", "style": "markdown"},
    "curseforge.md": {"source": "en", "style": "markdown"},
    "mcmod.md": {"source": "zh", "style": "mcmod"},
}

# 横幅：**正本**是仓库里的 design/banner.png，但对外引用的是用户自己上传的 GitHub 附件 URL。
#
# 【为什么不用 raw 链接】2026-09-21 用户实测：raw.githubusercontent.com 在他的网络下加载不出来；
# 而且 README 里写相对路径也没用 —— GitHub 渲染 README 时会把相对图片路径重写成 raw 域名，
# 于是"仓库门面"和"商店正文"会一起坏。附件 URL（user-attachments）是他自己传的，能加载。
# 【换横幅时要做三件事】换 design/banner.png → 重新上传拿到新 URL → 改这里的 BANNER_URL
# （README 两份由闸盯着必须与它一致）。
# 【长期更好的一档】Modrinth 项目建好后，把图传进它的图库、换成 cdn.modrinth.com 的地址
# （AtomChat 那两份文件就是这么做的；那个 CDN 在用户网络下也通）—— 到那天只改这一行。
BANNER_URL = "https://github.com/user-attachments/assets/5bbce647-df3a-4c51-b87b-d63dfc7c4bdc"

# 仓库里的横幅正本（用户在 GitHub 上手动上传后收进仓库的那一份，1395 字节的像素字 wordmark）。
# 它不进 jar，也不被正文直接引用 —— 它的作用是"附件没了还能再传一次"。
BANNER = "design/banner.png"

HEADING = re.compile(r"^#\s+(.+?)\s*$")
LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)]+)\)")


def read_properties(path: Path) -> dict[str, str]:
    """读 properties。**必须按 UTF-8** —— Properties.load(InputStream) 按 ISO-8859-1 解字节，
    中文身份值会变成乱码，而构建全程绿灯（2026-09-21 在 gradle.properties 上踩过）。"""
    props: dict[str, str] = {}
    if not path.is_file():
        return props
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        props[key.strip()] = value.strip()
    return props


def header_block() -> list[str]:
    """头图 + 标题 + 标语 + 徽章行。全部从仓库身份与目标声明里读，手写不出来漂。"""
    root = read_properties(ROOT / "gradle.properties")
    name = root.get("mod_name", "")
    tagline = root.get("mod_description", "")
    license_name = root.get("mod_license", "")
    repo = root.get("mod_repo_url", "").rstrip("/")
    if not (name and tagline and repo):
        raise SystemExit("gradle.properties 缺 mod_name / mod_description / mod_repo_url —— "
                         "商店正文的头图与徽章行从它们生成，读不到就不出")

    # 平台事实：MC 与加载器版本只在平台 properties 里，Java 在目标声明里
    platform = read_properties(ROOT / "platforms" / "1.20.1-forge" / "gradle.properties")
    mc = platform.get("minecraft_version", "")
    loader_version = platform.get("forge_version", "")
    targets = json.loads((ROOT / "versions" / "targets.json").read_text(encoding="utf-8"))
    forge_target = next((t for k, t in targets.items()
                         if not k.startswith("_") and t.get("buildable")), {})
    java = forge_target.get("java", "")
    loader = forge_target.get("loader", "Forge")

    badge = (
        f"![MC](https://img.shields.io/badge/MC-{mc}-green) "
        f"![Loader](https://img.shields.io/badge/Loader-{loader}-red) "
        f"![Side](https://img.shields.io/badge/Side-Client-blue) "
        f"![Java](https://img.shields.io/badge/Java-{java}%2B-yellow) "
        f"![Version](https://img.shields.io/github/v/release/{repo.split('github.com/')[-1]}?sort=semver) "
        f"![License](https://img.shields.io/badge/License-{license_name}-brightgreen)"
    )
    return [
        f"![{name}]({BANNER_URL})",
        f"# {name}",
        f"_{tagline}_",
        badge,
    ]


def parse_source(text: str) -> tuple[list[str], list[tuple[str, list[list[str]]]]]:
    """切成（一句话简介, [(标题, [块, …]), …]）。

    块 = 空行分隔的一段；同一种"形状"才合成一块（列表 / 表格 / 段落），
    否则"段落后面紧跟一张表"会被粘成一块、渲染出错误的排版。
    """
    tagline: list[str] = []
    sections: list[tuple[str, list[list[str]]]] = []
    block: list[str] = []

    def shape(line: str) -> str:
        if line.startswith("- "):
            return "list"
        if line.startswith("|"):
            return "table"
        return "text"

    def flush() -> None:
        nonlocal block
        if block:
            if sections:
                sections[-1][1].append(block)
            block = []

    for raw in text.splitlines():
        line = raw.rstrip()
        heading = HEADING.match(line)
        if heading:
            flush()
            sections.append((heading.group(1), []))
            continue
        if not line.strip():
            flush()
            continue
        if block and shape(block[0]) != shape(line):
            flush()
        block.append(line)
    flush()
    return tagline, sections


def mcmod_plain(text: str) -> str:
    """把一行 Markdown 抹成**纯文本** —— 这是 MC 百科那份最关键的一步。

    2026-09-21 用户原话：「**mcmod 里还保留 markdown 写法？mcmod 编辑器就不支持代码格式，
    只能用他们的可视化编辑器去改格式**」。百科正文里出现 `**`、反引号、`[文字](链接)`、`- ` 这类
    标记，页面上就是一堆没被解析的符号（他们的编辑器只认自己的工具栏，不解析 Markdown）。

    所以：粗体去星号、代码去反引号、链接留文字、列表去前缀。`tools/verify_targets.py` 会再扫一遍，
    漏一个就红 —— 别只靠这里自觉。
    """
    text = LINK.sub(r"\1", text)                    # [文字](url) -> 文字
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)    # **粗体**   -> 粗体
    text = re.sub(r"^\s*[-*]\s+", "", text)         # - 条目     -> 条目
    return text.replace("`", "")


def mcmod_table(block: list[str]) -> list[str]:
    """Markdown 表 → 百科的行式表：`格| 格；`（AtomChat 的百科页就是这么写的）。

    表头与 `| --- |` 分隔行丢掉（百科那边不需要表头）。
    """
    rows = []
    for i, line in enumerate(block):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if i == 0 or all(set(c) <= set("-: ") for c in cells):
            continue                      # 表头 / 分隔行
        rows.append("| ".join(cells) + "；")
    return rows


# 百科里当"大标题"（[h1=]）的节：概述与收尾那几节。中间的正文节用 [h2=] 挂在概述下面 ——
# 这是 AtomChat 那页的实际层级（[h1=概述] → [h2=原版优化] … → [h1=常见问题] → [h1=问题反馈]）。
MCMOD_H1 = {"概述", "常见问题", "更新日志", "问题反馈", "画廊"}


def render(tagline: list[str], sections: list[tuple[str, list[list[str]]]], style: str) -> str:
    out: list[str] = []
    one_liner = " ".join(t.strip() for t in tagline) if tagline else ""
    if style == "markdown":
        for line in header_block():
            out += [line, ""]
        for title, blocks in sections:
            out += [f"## {title}", ""]
            for block in blocks:
                out += block            # 列表：一条一行；表格：原样；段落：整段连着
                out.append("")
    else:  # mcmod：百科的可视化编辑器不认 Markdown —— 标记全抹掉，条目之间空一行，表格走行式
        out += ["[mark:title_menu]", ""]
        if one_liner:
            out += [mcmod_plain(one_liner), ""]
        for title, blocks in sections:
            level = "h1" if title in MCMOD_H1 else "h2"
            out += [f"[{level}={title}]", ""]
            for block in blocks:
                lines = mcmod_table(block) if block[0].startswith("|") else block
                for line in lines:
                    out.append(mcmod_plain(line))
                    out.append("")
    while out and not out[-1]:
        out.pop()
    return "\n".join(out) + "\n"


def render_all() -> dict[str, str]:
    parsed = {}
    for lang, path in SOURCES.items():
        if not path.is_file():
            raise SystemExit(f"找不到正本 {path.relative_to(ROOT)}")
        parsed[lang] = parse_source(path.read_text(encoding="utf-8"))
    return {name: render(*parsed[spec["source"]], spec["style"])
            for name, spec in OUTPUTS.items()}


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true", help="重出三份产物")
    group.add_argument("--check", action="store_true", help="只校验，不写盘")
    args = parser.parse_args()

    rendered = render_all()
    problems = []
    for name, text in rendered.items():
        path = STORE / name
        current = path.read_text(encoding="utf-8") if path.is_file() else None
        if current == text:
            continue
        if args.check:
            problems.append(f"docs/store/{name} 与正本不一致"
                            + ("（文件不存在）" if current is None else ""))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            # newline="\n" 是必须的：Windows 上 write_text 默认写 CRLF，而本仓库 .gitattributes
            # 是 eol=lf（工作区必须 LF，否则每次提交都要被 git 警告一遍）
            path.write_text(text, encoding="utf-8", newline="\n")
            print(f"  write docs/store/{name}（{len(text.splitlines())} 行）")

    if problems:
        print("store_copy: 产物不是从正本出的 —— 改内容请改 docs/store/{zh,en}.md，"
              "然后跑 python tools/store_copy.py --write", file=sys.stderr)
        for p in problems:
            print(f"  ✗ {p}", file=sys.stderr)
        return 1
    print(f"store_copy: 三份产物与正本一致（{', '.join(sorted(rendered))}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
