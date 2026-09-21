#!/usr/bin/env python3
"""校验多目标仓库的结构是否自洽。

这套结构的价值全在"声明得出来、却编不过去的那种矛盾，要在 CI 就红"。
这个脚本就是那道闸。它读版本声明（不读文档、不读注释），把互相矛盾的地方找出来。

检查项：

  1. 矩阵是规则     每个 Minecraft 版本都要有 Fabric / NeoForge / Forge 三条（缺了要显式记 buildable:false）
  2. 身份只有一份   仓库根 gradle.properties 必须有全部身份键，且值与 LICENSE 对得上
  3. 工程与条目互存 工程目录存在 → 矩阵里必须有它；矩阵里的 project → 目录必须存在
  4. 谓词与挂载一致 挂了版本层就必须够得到那个版本；加载器层/映射层同理
  5. 层目录由声明   层的目录路径必须能从它钉的轴推出来
  6. populated 如实 声明 populated:true 的层不能是空的；声明 false 的不能有内容
  7. 平台不许定义身份  各平台 gradle.properties 里不许出现身份键（否则就是第二个真源）
  8. buildable 不许有未验证字段
  9. 仓库地址只有一份  资源与文档里出现的本仓库地址必须与 mod_repo_url 一致，且不许硬编码进资源
 10. 商店正文两份正本  docs/store/{zh,en}.md 逐节对等；三份产物必须等于从正本重生成的结果

用法：python tools/verify_targets.py
退出码 0 = 全绿，1 = 有问题（问题打在 stderr）。
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# 仓库级身份：只在根 gradle.properties 里，各平台不许重复定义
IDENTITY_KEYS = [
    "mod_id", "mod_name", "mod_license", "mod_group_id",
    "mod_authors", "mod_description", "mod_repo_url", "mod_credits", "mod_version",
]

# 这些身份键的值不许是占位符 —— 占位值跟实测值长得一模一样，只有对着名单才认得出来
NO_PLACEHOLDER_KEYS = [
    "mod_id", "mod_name", "mod_license", "mod_group_id",
    "mod_authors", "mod_description", "mod_repo_url", "mod_credits",
]
PLACEHOLDER_VALUES = {"", "UNSET", "TBD", "TODO", "N/A", "none"}

# 矩阵规则：每个 MC 版本都要有这三个加载器
REQUIRED_LOADERS = ["Fabric", "NeoForge", "Forge"]

# 轴 → 目录名（与 pickupcard-layers.gradle 里的 AXIS_DIR 必须一致）
AXIS_DIR = {"since": "version", "loader": "loader", "mappings": "mapping"}

problems: list[str] = []


def fail(message: str) -> None:
    problems.append(message)


def read_properties(path: Path) -> dict[str, str]:
    """读 gradle.properties。只认 key=value，忽略注释与空行。"""
    result: dict[str, str] = {}
    if not path.is_file():
        return result
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        result[key.strip()] = value.strip()
    return result


def version_at_least(have: str, want: str) -> bool:
    """have >= want？非数字段当 0 处理，与 Gradle 侧保持同一种比较。"""
    def parts(v: str) -> list[int]:
        out = []
        for token in v.split("."):
            out.append(int(token) if token.isdigit() else 0)
        return out

    a, b = parts(have), parts(want)
    for i in range(max(len(a), len(b))):
        x = a[i] if i < len(a) else 0
        y = b[i] if i < len(b) else 0
        if x != y:
            return x > y
    return True


def entries(data: dict) -> dict:
    """去掉 _comment 之类的元键，只留真正的条目。"""
    return {k: v for k, v in data.items() if not k.startswith("_")}


# 仓库地址出现在任何地方（shields 徽章、actions 徽章、release 链接）都是这个形状
REPO_URL_IN_TEXT = re.compile(r"(?:github\.com|shields\.io/github/[a-z/]*?)/([\w.-]+)/([\w.-]+)")
GITHUB_REPO_URL = re.compile(r"^https://github\.com/([\w.-]+)/([\w.-]+)$")

stats = {"links": 0}


def check_identity_values(props: dict[str, str]) -> None:
    """身份键的值要如实：不留占位符，署名与许可还要与 LICENSE 正本对得上。"""
    for key in NO_PLACEHOLDER_KEYS:
        value = props.get(key, "").strip()
        if value in PLACEHOLDER_VALUES:
            fail(f"身份键 {key} 的值是占位符「{value}」—— 占位值跟真值长得一模一样，"
                 f"只有对着名单才认得出来")

    license_name = props.get("mod_license", "").strip()
    license_file = ROOT / "LICENSE"
    if not license_file.is_file():
        fail("仓库根没有 LICENSE —— mods.toml 的 license 指着它，jar 里也要带上它")
        return
    text = license_file.read_text(encoding="utf-8", errors="replace")
    # 许可证名按整词找；带连字符的（Apache-2.0）退一步用 "-" 前的第一段去比（"Apache"）
    names = [license_name] + ([license_name.split("-")[0]] if "-" in license_name else [])
    if license_name and not any(re.search(rf"\b{re.escape(n)}\b", text, re.IGNORECASE) for n in names):
        fail(f"mod_license={license_name}，但仓库根的 LICENSE 里找不到这个名字 —— "
             f"署名与许可声明必须对得上，否则 jar 里那份声明说的是另一件事")

    repo_url = props.get("mod_repo_url", "").strip()
    if not GITHUB_REPO_URL.match(repo_url):
        fail(f"mod_repo_url={repo_url} 不是 https://github.com/<owner>/<repo> 的形状")


def check_repo_url(props: dict[str, str]) -> None:
    """仓库地址只许有一个来源。

    两件事一起查，因为它们的病根是同一个 —— "同一个地址写在很多地方，改的时候只改了几处"：

      · **资源文件里不许硬编码**（`platforms/*/src/main/resources/**`）。那些文件进 jar，
        出了错是玩家在游戏里点一个打不开的链接，而构建从头到尾是绿的。
      · **README / docs 里的本仓库链接必须与 mod_repo_url 一致**。仓库改名、换账号、
        换 owner 之后，文档里的链接会一处一处烂掉，没有任何构建会失败。

    指向同一个 owner 下**别的**仓库（兄弟项目，如 UIDeck）以及上游仓库（如 memononen/nanovg）
    的链接一律放行：那是真的别的地址，不是漂移。
    """
    repo_url = props.get("mod_repo_url", "").strip()
    match = GITHUB_REPO_URL.match(repo_url)
    if not match:
        return  # 形状都不对，上面的 check_identity_values 已经报过了
    owner, repo = match.group(1), match.group(2)
    head = repo_url.removesuffix(".git")

    for name, target in entries(json.loads((ROOT / "versions/targets.json").read_text("utf-8"))).items():
        project = target.get("project")
        if not project:
            continue
        resources = ROOT / project / "src" / "main" / "resources"
        if not resources.is_dir():
            continue
        for path in sorted(resources.rglob("*")):
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for found in REPO_URL_IN_TEXT.finditer(text):
                fail(f"{path.relative_to(ROOT).as_posix()} 里硬编码了 "
                     f"github.com/{found.group(1)}/{found.group(2)} —— 仓库地址的唯一来源是"
                     f" gradle.properties 的 mod_repo_url（资源里写 ${{mod_repo_url}}）")

    doc_files = [ROOT / "README.md", ROOT / "README_EN.md"]
    if (ROOT / "docs").is_dir():
        doc_files += sorted(p for p in (ROOT / "docs").glob("*.md"))
    for path in doc_files:
        if not path.is_file():
            continue
        for found in REPO_URL_IN_TEXT.finditer(path.read_text(encoding="utf-8", errors="replace")):
            found_owner, found_repo = found.group(1), found.group(2)
            stats["links"] += 1
            if found_repo.lower() == repo.lower():
                if found_owner != owner:
                    fail(f"{path.relative_to(ROOT).as_posix()} 里的 "
                         f"github.com/{found_owner}/{found_repo} 与本仓库 mod_repo_url（{head}）"
                         f"不是同一处 —— 仓库地址搬过家就别只改一半")
            # 同 owner 下的兄弟仓库、以及上游仓库：都是真的别的地址，放行


# 商店正文：**两份正本 → 三份产物**（`tools/store_copy.py`）。
# 用户 2026-09-21 的原话是「好乱啊怎么办」—— 那天仓库里躺着四份描述文件、两种标记、两种语言，
# 手写几遍就要同步几遍。现在只有两份人写的正本，产物是生成的。
STORE_SOURCES = ["docs/store/zh.md", "docs/store/en.md"]

# 产物名（与 tools/store_copy.py 的 OUTPUTS 一致；这里只用来报数）
OUTPUT_FILES = ["modrinth.md", "curseforge.md", "mcmod.md"]

# 数条目与抽事实时要跳过的"版式行"：头图 / 标题 / 斜体标语 / 徽章 / 百科的目录标记。
# （正本里本来不该有这些，排掉是为了让闸也能读产物。）
STORE_LAYOUT_LINE = re.compile(r"^(?:!\[|<img|#|_|\[mark)|img\.shields\.io")

# 事实 token：反引号里的标识符 / 键名 / 路径，以及数字（含 ms、% 这类单位）。
# 归一化去掉反引号与空白 —— "0.9 ms" 与 "0.9ms" 是同一件事（changelog 那套闸踩过这个坑）。
FACT_TOKEN = re.compile(r"`[^`]+`|\d+(?:\.\d+)*(?:\s*(?:ms|%|MB|KB))?")





def first_image_url(text: str) -> str | None:
    """取正文里的第一张图：Markdown `![…](url)` 或 HTML `<img … src="url">`。

    README 里的图允许写**相对路径**（GitHub 上直接就能渲染），所以这里两种都给出来。
    """
    for pattern in (r"!\[[^\]]*\]\(([^)\s]+)\)",
                    r"<img[^>]*\ssrc=[\"']([^\"']+)[\"']"):
        match = re.search(pattern, text)
        if match:
            return match.group(1)
    return None


RAW_HOST = "https://raw.githubusercontent.com/"


def banner_key(value: str | None) -> str | None:
    """把"门面 / 商店用的那张横幅"归一成可比的形式。

    README 写相对路径（`design/banner.png`），商店正文必须写绝对 URL（正文没有基准路径），
    两者指的是仓库里同一份文件 —— 所以 key = 去掉 raw 域名与 owner/仓库/分支之后的仓库内路径。
    """
    if not value:
        return None
    if value.startswith(RAW_HOST):
        parts = value[len(RAW_HOST):].split("/")
        return "/".join(parts[3:]) if len(parts) > 3 else value
    return value.lstrip("./")



def split_sections(text: str) -> list[tuple[str, str]]:
    """按 `# 标题` 切段。正本两份共用同一种中立标记（产物才带平台标记）。"""
    heading = re.compile(r"^#\s+(.+?)\s*$")
    sections: list[tuple[str, list[str]]] = []
    for line in text.splitlines():
        match = heading.match(line)
        if match:
            sections.append((match.group(1), []))
            continue
        if sections:
            sections[-1][1].append(line)
    return [(head, "\n".join(body)) for head, body in sections]


def store_items(body: str) -> int:
    """数条目：非空行各算一条（版式行不计）。

    正本两份都是"一条一行"的形状，所以这一条跨语言可比 —— 而两者的**标记差异**已经由
    `store_copy.py` 承担了，闸不用再为"表格 vs 列表"绕路。
    """
    return sum(1 for line in body.splitlines()
               if line.strip() and not STORE_LAYOUT_LINE.search(line.strip()))


def store_facts(body: str) -> set[str]:
    """抽事实 token（反引号标识符 + 数字/单位），归一化掉反引号与空白。

    **先把 URL 抹掉**：`https://github.com/E33EPUS/...` 里的 "33" 会被当成一个数字事实，
    两份正本只要一处写链接另一处没写，就是一片假报。
    """
    facts = set()
    for line in body.splitlines():
        if STORE_LAYOUT_LINE.search(line.strip()):
            continue
        for token in FACT_TOKEN.findall(re.sub(r"https?://\S+", "", line)):
            cleaned = token.replace("`", "").replace(" ", "").strip()
            if cleaned:
                facts.add(cleaned)
    return facts


def check_store_sources() -> None:
    """两份正本必须逐节对等：节数相同、每节条目数相同、每节事实 token 两边都不许少。

    【为什么不再需要节表映射】两份正本用同一种中立标记、同一个顺序，第 N 节对第 N 节 ——
    2026-09-21 之前那张 EN↔ZH 的节表映射是"两份文件各写各的标记"逼出来的，现在没有了。
    """
    paths = [ROOT / rel for rel in STORE_SOURCES]
    if not all(p.is_file() for p in paths):
        return  # 还没上架的仓库可以没有，但一旦有了就得对齐

    (zh_path, en_path) = paths  # STORE_SOURCES = [zh, en]
    zh = split_sections(zh_path.read_text(encoding="utf-8"))
    en = split_sections(en_path.read_text(encoding="utf-8"))

    if len(zh) != len(en):
        fail(f"商店正本节数不等：{STORE_SOURCES[0]} {len(zh)} 节 vs "
             f"{STORE_SOURCES[1]} {len(en)} 节\n      "
             f"中文 {[h for h, _ in zh]}\n      英文 {[h for h, _ in en]}")
    for i, ((zh_head, zh_body), (en_head, en_body)) in enumerate(zip(zh, en), 1):
        label = f"第 {i} 节（{zh_head} / {en_head}）"
        zh_count, en_count = store_items(zh_body), store_items(en_body)
        if zh_count != en_count:
            fail(f"商店正本{label} 条目数不等：中文 {zh_count} 条 vs 英文 {en_count} 条 —— "
                 f"内容要相同，只许语言不同")
        zh_facts, en_facts = store_facts(zh_body), store_facts(en_body)
        missing_in_en = sorted(zh_facts - en_facts)
        missing_in_zh = sorted(en_facts - zh_facts)
        if missing_in_en:
            fail(f"商店正本{label} 里这些事实只在中文、英文那边没有：{missing_in_en}")
        if missing_in_zh:
            fail(f"商店正本{label} 里这些事实只在英文、中文那边没有：{missing_in_zh}")


def check_store_outputs() -> None:
    """三份产物必须等于"从正本重生成"的结果，且头图与 README 顶部那张同源。

    【为什么这条比"三份互相比对"硬】比对只能发现两处不一致；重生成能发现**任何**手工改动 ——
    包括"改了产物忘了改正本"（那是最容易发生的漂，因为产物才是大家顺手编辑的那份文件）。
    """
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    try:
        import store_copy
    except ImportError as exc:  # pragma: no cover - 只有文件被挪走才走得到
        fail(f"读不到 tools/store_copy.py（{exc}）—— 商店产物由它生成，缺了就没法校验")
        return

    rendered = store_copy.render_all()
    for name, expected in rendered.items():
        path = store_copy.STORE / name
        actual = path.read_text(encoding="utf-8") if path.is_file() else None
        if actual != expected:
            fail(f"docs/store/{name} 与正本重生成的结果不一致"
                 + ("（文件不存在）" if actual is None else "")
                 + " —— 改内容请改 docs/store/{zh,en}.md，然后跑 "
                   "python tools/store_copy.py --write")

    readme = ROOT / "README.md"
    if readme.is_file():
        store_banner = store_copy.BANNER
        readme_image = banner_key(first_image_url(readme.read_text(encoding="utf-8")))
        if readme_image and readme_image != banner_key(store_banner):
            fail(f"README 顶部那张图（{readme_image}）与商店正文头图（{store_banner}）"
                 f"不是同一张 —— 门面与商店各用各的图，就没有东西会把它们对起来")




def main() -> int:
    raw_targets = json.loads((ROOT / "versions/targets.json").read_text(encoding="utf-8"))
    targets = entries(raw_targets)
    layers = entries(json.loads((ROOT / "versions/layers.json").read_text(encoding="utf-8")))

    # ---- 1. 身份只有一份（值也要如实、地址也要只有一份） ----
    root_props = read_properties(ROOT / "gradle.properties")
    for key in IDENTITY_KEYS:
        if key not in root_props:
            fail(f"仓库根 gradle.properties 缺身份键 {key}")
    check_identity_values(root_props)
    check_repo_url(root_props)
    check_store_sources()
    check_store_outputs()
    store_outputs = sorted(OUTPUT_FILES)

    # ---- 2. 矩阵是规则 ----
    by_version: dict[str, set[str]] = {}
    for name, t in targets.items():
        by_version.setdefault(t.get("minecraft", "?"), set()).add(t.get("loader", "?"))
    for version, loaders in sorted(by_version.items()):
        missing = [ld for ld in REQUIRED_LOADERS if ld not in loaders]
        if missing:
            # 缺的必须在矩阵里以 buildable:false 的条目显式出现，而不是压根没有
            for ld in missing:
                if not any(t.get("minecraft") == version and t.get("loader") == ld
                           for t in targets.values()):
                    fail(f"Minecraft {version} 缺 {ld} 条目，且没有任何 buildable:false 的占位说明"
                         f"（矩阵是规则：缺哪一格应该在 targets.json 里看得见）")

    # ---- 3. 工程与条目互存 ----
    for name, t in targets.items():
        project = t.get("project")
        if not project:
            fail(f"目标 {name} 没有 project 字段")
            continue
        project_dir = ROOT / project
        # 目录必须存在 —— 但只对 buildable:true 成立。buildable:false 是"这个组合没做"
        # 的显式占位（否则它会变成想不起来），那种条目本来就没有工程目录。
        if t.get("buildable") and not project_dir.is_dir():
            fail(f"目标 {name} 声明了 buildable:true 与 {project}，但那个目录不存在 —— "
                 f"CI 会去编它，然后失败")
        # buildable 不许留未验证字段
        if t.get("buildable") and t.get("unverified"):
            fail(f"目标 {name} 是 buildable:true，却还留着 unverified={t['unverified']}"
                 f"（已发版的目标不许有猜出来的字段）")
        # 平台不许定义身份（目录不存在就没什么可查的）
        platform_props = read_properties(project_dir / "gradle.properties")
        for key in IDENTITY_KEYS:
            if key in platform_props:
                fail(f"{project}/gradle.properties 定义了身份键 {key} —— "
                     f"身份的唯一来源是仓库根，各平台定义就是第二个真源")

    # 反向：工程目录存在就必须在矩阵里有声明 —— 本地不编、CI 也跳过的源码不许躺着
    platforms_dir = ROOT / "platforms"
    if platforms_dir.is_dir():
        declared = {t.get("project") for t in targets.values()}
        for child in sorted(platforms_dir.iterdir()):
            if not child.is_dir():
                continue
            rel = child.relative_to(ROOT).as_posix()
            if rel not in declared:
                fail(f"工程目录 {rel} 存在，但 versions/targets.json 里没有它 —— "
                     f"这是一份没有任何地方验证的源码（本地不编、CI 也跳过）")

    # ---- 4/5/6. 层：谓词一致、目录由声明推出来、populated 如实 ----
    for name, layer in layers.items():
        axes = [ax for ax in ("since", "loader", "mappings") if ax in layer]
        if not axes:
            fail(f"层 {name} 一个轴都没钉（since / loader / mappings）—— 一个都不写就是 shared/，不是层")
            continue
        expected = ROOT / "layers" / "+".join(sorted(AXIS_DIR[ax] for ax in axes)) / name
        has_content = expected.is_dir() and any(p.is_file() for p in expected.rglob("*"))
        populated = bool(layer.get("populated"))
        # 目录路径由声明推出来 —— 声明得出来、编不过去的那种矛盾就是靠这条消掉的。
        # 但目录【不存在】是允许的，只要声明的 populated 与之相符：层可以先声明后填，
        # 空的层（populated:false）本来就没有目录，这正是"空是显式声明状态"的落点。
        if populated and not has_content:
            fail(f"层 {name} 声明 populated:true，但 {expected.relative_to(ROOT).as_posix()}"
                 f" 不存在或是空的")
        if not populated and has_content:
            fail(f"层 {name} 声明 populated:false，却扫到了内容（空要是显式声明的状态）")

    # 挂载的层必须满足它的谓词
    for name, t in targets.items():
        for layer_name in t.get("layers", []) or []:
            if layer_name not in layers:
                fail(f"目标 {name} 引用了不存在的层 {layer_name}")
                continue
            layer = layers[layer_name]
            if "since" in layer:
                if not version_at_least(str(t.get("minecraft")), str(layer["since"])):
                    fail(f"目标 {name}（MC {t.get('minecraft')}）挂了版本层 {layer_name}"
                         f"（since {layer['since']}）—— 它够不到")
            if "loader" in layer and str(t.get("loader")) != str(layer["loader"]):
                fail(f"目标 {name} 的加载器是 {t.get('loader')}，"
                     f"却挂了要求 loader={layer['loader']} 的层 {layer_name}")
            if "mappings" in layer and str(t.get("mappings")) != str(layer["mappings"]):
                fail(f"目标 {name} 的映射是 {t.get('mappings')}，"
                     f"却挂了要求 mappings={layer['mappings']} 的层 {layer_name}")

    # ---- 报告 ----
    total_checks = (
        len(targets) * 3      # 每个目标：身份/工程/层 大致各几项
        + len(layers) * 3
        + len(IDENTITY_KEYS)
        + len(REQUIRED_LOADERS) * max(1, len(by_version))
    )
    if problems:
        print(f"verify_targets: {len(problems)} 个问题（共约 {total_checks} 项检查）", file=sys.stderr)
        for p in problems:
            print(f"  ✗ {p}", file=sys.stderr)
        return 1

    print(f"verify_targets: 全绿（{len(targets)} 个目标，{len(layers)} 个层，"
          f"约 {total_checks} 项检查；身份 {len(IDENTITY_KEYS)} 键、仓库链接 {stats['links']} 处一致、"
          f"商店正本 {len(STORE_SOURCES)} 份 → 产物 {len(store_outputs)} 份）")
    for version in sorted(by_version):
        loaders = ", ".join(sorted(by_version[version]))
        print(f"  MC {version}: {loaders}")
    return 0


def matrix_entries(raw_targets: dict) -> list[dict]:
    """给 CI 用的构建矩阵：**有工程目录的目标才编**（所以工作流里没有目标名）。

    后缀也在这里算好放进矩阵，不在 workflow 里拼表达式 —— GitHub 表达式里
    `a && '' || b` 会因为空串是 falsy 而静默取到 b，那种错只有真发版时才看得见
    （AtomChat 那边踩过：把"未验证"后缀贴到了正式产物上）。
    """
    out = []
    for name, t in sorted(entries(raw_targets).items()):
        project = t.get("project")
        if not project or not (ROOT / project).is_dir():
            continue
        out.append({
            "target": name,
            "project": project,
            "java": t.get("java"),
            "suffix": "" if t.get("buildable") else "-unverified",
        })
    return out


if __name__ == "__main__":
    argv = sys.argv[1:]
    matrix_out = None
    if "--matrix-out" in argv:
        idx = argv.index("--matrix-out")
        if idx + 1 >= len(argv):
            print("verify_targets: --matrix-out 后面要跟一个路径", file=sys.stderr)
            sys.exit(2)
        matrix_out = argv[idx + 1]
    code = main()
    if code == 0 and matrix_out:
        raw = json.loads((ROOT / "versions/targets.json").read_text(encoding="utf-8"))
        payload = matrix_entries(raw)
        Path(matrix_out).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        print("  构建矩阵 -> {}（{} 个目标：{}）".format(
            matrix_out, len(payload), ", ".join(e["target"] + e["suffix"] for e in payload)))
    sys.exit(code)
