#!/usr/bin/env python3
"""从两份 README 派生三处商店正文（**不落库**）。

【为什么以 README 为正本】2026-09-21 用户的原话：「**别在仓库 docs 里放描述文档了，我上传商店
是一定会微调的，到时候又不统一**」。说穿了：商店页面是在**平台编辑器里**改的，仓库里再放一份
"描述文档"就必然与线上分家 —— 而且分家看不出来（没有任何东西会把它们对起来）。
README 不一样：它是仓库门面、是唯一一处你本来就会改的描述，而且 `README.md` / `README_EN.md`
的 13 个 `##` 节落在**完全相同的行号**（双语对齐是当年就设计进去的）。

所以：**正本 = README.md + README_EN.md**，三处商店正文是**派生视图**，需要时现出、不提交。

    README.md  (中文)  ┐
                       ├─→ Modrinth 正文   （英文 + 头图 + 徽章行，Markdown）
    README_EN.md (英文)┤─→ CurseForge 正文 （与 Modrinth 逐字节相同，人工贴）
                       └─→ MC 百科正文     （中文纯文本 + [h1=]/[h2=]，人工贴）

用法：
    python tools/store_copy.py --platform modrinth     # 打印（送进 store_sync 或自己复制）
    python tools/store_copy.py --platform curseforge --out %TEMP%\\cf.md
    python tools/store_copy.py --platform mcmod
退出码：0 = 出得来；1 = README 里少了要用的那一节（映射表该跟着改）。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SOURCES = {"zh": ROOT / "README.md", "en": ROOT / "README_EN.md"}

# 平台 → （用哪份 README, 标记风格）
PLATFORMS = {
    "modrinth": ("en", "markdown"),
    "curseforge": ("en", "markdown"),
    "mcmod": ("zh", "mcmod"),
}

# 【README 的哪些节进商店正文】映射表，值是（README 里的节名, 商店正文里的节名）。
#
# 两个读者不一样：README 是仓库门面（还要讲怎么构建、去哪看文档、许可是什么），
# 商店页只回答"装不装、好不好用"。所以安装/快速开始/自定义卡面/更多文档/开发与构建/许可证
# 不进正文 —— 前两者的信息在徽章行与概述里，后三者是仓库自己事。
#
# 表里每个名字都必须在 README 里真的存在，否则闸会红：不然改个 README 节名，商店正文就会
# **静默少一节**（正是这个项目一直在治的病）。
MAP = {
    "zh": [("这是什么", "概述"), ("主要功能", "功能"), ("兼容性", "兼容性"),
           ("配置", "配置"), ("已知限制", "已知限制"), ("常见问题", "常见问题")],
    "en": [("What it is", "Overview"), ("Features", "Features"), ("Compatibility", "Compatibility"),
           ("Configuration", "Configuration"), ("Known limitations", "Known Limitations"),
           ("FAQ", "FAQ")],
}

FEEDBACK_TITLE = {"zh": "问题反馈", "en": "Feedback"}

# 横幅：**正本**是仓库里的 design/banner.png，对外引用的是用户自己上传的 GitHub 附件 URL。
# 【为什么不用 raw 链接】2026-09-21 用户实测：raw.githubusercontent.com 加载不出来；README 写相对
# 路径也没用（GitHub 渲染时会把相对图片路径重写成 raw 域名）—— 门面与商店会一起坏。
# 【换横幅】换 design/banner.png → 重新上传拿新 URL → 改这一行（README 两处由闸盯着必须一致）。
BANNER_URL = "https://github.com/user-attachments/assets/5bbce647-df3a-4c51-b87b-d63dfc7c4bdc"
BANNER = "design/banner.png"

HEADING = re.compile(r"^##\s+(.+?)\s*$")
TAGLINE = re.compile(r"<em>(.+?)</em>")
LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)]+)\)")

# 百科里当"大标题"（[h1=]）的节：概述与收尾那几节。中间的正文节用 [h2=] 挂在概述下面 ——
# 这是 AtomChat 那页的实际层级。
MCMOD_H1 = {"概述", "常见问题", "更新日志", "问题反馈", "画廊"}


def read_properties(path: Path) -> dict[str, str]:
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


def readme_sections(lang: str) -> tuple[str, dict[str, list[list[str]]]]:
    """读一份 README：返回（一句话简介, {节名: [块, …]}）。

    块 = 空行分隔的一段；同一种"形状"才合成一块（列表 / 表格 / 段落），
    免得"段落后面紧跟一张表"被粘成一块、渲染出错误的排版。
    """
    path = SOURCES[lang]
    if not path.is_file():
        raise SystemExit(f"找不到 {path.name}")
    text = path.read_text(encoding="utf-8")
    match = TAGLINE.search(text)
    tagline = match.group(1).strip() if match else ""

    sections: dict[str, list[list[str]]] = {}
    title: str | None = None
    block: list[str] = []

    def shape(line: str) -> str:
        if line.startswith("- "):
            return "list"
        if line.startswith("|"):
            return "table"
        return "text"

    def flush() -> None:
        nonlocal block
        if block and title is not None:
            sections[title].append(block)
        block = []

    for raw in text.splitlines():
        line = raw.rstrip()
        heading = HEADING.match(line)
        if heading:
            flush()
            title = heading.group(1)
            sections[title] = []
            continue
        if title is None:          # 标题之前是头图/标语/徽章，不进正文
            continue
        if not line.strip():
            flush()
            continue
        if block and shape(block[0]) != shape(line):
            flush()
        block.append(line)
    flush()
    return tagline, sections


def header_block() -> list[str]:
    """头图 + 标题 + 标语 + 徽章行。全部从仓库身份与目标声明里读，手写不出来漂。"""
    root = read_properties(ROOT / "gradle.properties")
    name = root.get("mod_name", "")
    license_name = root.get("mod_license", "")
    repo = root.get("mod_repo_url", "").rstrip("/")
    if not (name and repo):
        raise SystemExit("gradle.properties 缺 mod_name / mod_repo_url —— 头图与徽章行从它们生成")

    platform = read_properties(ROOT / "platforms" / "1.20.1-forge" / "gradle.properties")
    mc = platform.get("minecraft_version", "")
    targets = json.loads((ROOT / "versions" / "targets.json").read_text(encoding="utf-8"))
    forge_target = next((t for k, t in targets.items()
                         if not k.startswith("_") and t.get("buildable")), {})
    java = forge_target.get("java", "")
    loader = forge_target.get("loader", "Forge")
    slug = repo.split("github.com/")[-1]

    badge = (
        f"![MC](https://img.shields.io/badge/MC-{mc}-green) "
        f"![Loader](https://img.shields.io/badge/Loader-{loader}-red) "
        f"![Side](https://img.shields.io/badge/Side-Client-blue) "
        f"![Java](https://img.shields.io/badge/Java-{java}%2B-yellow) "
        f"![Version](https://img.shields.io/github/v/release/{slug}?sort=semver) "
        f"![License](https://img.shields.io/badge/License-{license_name}-brightgreen)"
    )
    return [f"![{name}]({BANNER_URL})", f"# {name}", badge]


def mcmod_plain(text: str) -> str:
    """把一行 Markdown 抹成**纯文本** —— MC 百科那份最关键的一步。

    用户原话：「**mcmod 里还保留 markdown 写法？编辑器就不支持代码格式，只能用他们的可视化
    编辑器去改格式**」。百科正文里出现 `**`、反引号、`[文字](链接)`、`- ` 这类标记，
    页面上就是一堆没被解析的符号（他们的编辑器只认自己的工具栏）。
    """
    text = LINK.sub(r"\1", text)                    # [文字](url) -> 文字
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)    # **粗体**   -> 粗体
    text = re.sub(r"^\s*[-*]\s+", "", text)         # - 条目     -> 条目
    return text.replace("`", "")


def mcmod_table(block: list[str]) -> list[str]:
    """Markdown 表 → 百科的行式表：`格| 格；`（AtomChat 那页就是这么写的）。表头与分隔行丢掉。"""
    rows = []
    for i, line in enumerate(block):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if i == 0 or all(set(c) <= set("-: ") for c in cells):
            continue
        rows.append("| ".join(cells) + "；")
    return rows


def render(platform: str) -> str:
    """出一份商店正文。MARK/两个 md 平台靠 header_block 补齐头图与徽章，百科那份全靠转换。"""
    lang, style = PLATFORMS[platform]
    tagline, sections = readme_sections(lang)
    props = read_properties(ROOT / "gradle.properties")
    issues = f"{props.get('mod_repo_url', '').rstrip('/')}/issues"

    picked: list[tuple[str, list[list[str]]]] = []
    for readme_title, store_title in MAP[lang]:
        if readme_title not in sections:
            raise SystemExit(
                f"{SOURCES[lang].name} 里没有「{readme_title}」这一节 —— 商店正文的映射表"
                f"（tools/store_copy.py 的 MAP）要跟着 README 一起改；"
                f"不报出来的话，商店正文会静默少一节")
        picked.append((store_title, sections[readme_title]))
    picked.append((FEEDBACK_TITLE[lang], [[issues]]))

    out: list[str] = []
    if style == "markdown":
        for line in header_block():
            out += [line, ""]
        out += [f"_{tagline}_", ""]
        for title, blocks in picked:
            out += [f"## {title}", ""]
            for block in blocks:
                out += block            # 列表：一条一行；表格：原样；段落：整段连着
                out.append("")
    else:  # mcmod：纯文本，一行一条，不写空行（编辑器会吃空行），只留 [h1=]/[h2=]
        if tagline:
            out += [mcmod_plain(tagline)]
        for title, blocks in picked:
            level = "h1" if title in MCMOD_H1 else "h2"
            out += [f"[{level}={title}]"]
            for block in blocks:
                lines = mcmod_table(block) if block[0].startswith("|") else block
                out += [mcmod_plain(line) for line in lines]
    while out and not out[-1]:
        out.pop()
    return "\n".join(out) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", required=True, choices=sorted(PLATFORMS))
    parser.add_argument("--out", type=Path, help="写进文件（缺省打到标准输出）")
    args = parser.parse_args()

    text = render(args.platform)
    if args.out:
        args.out.write_text(text, encoding="utf-8", newline="\n")
        print(f"store_copy: {args.platform} 正文 -> {args.out}（{len(text.splitlines())} 行）",
              file=sys.stderr)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
