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
 10. 描述只有一处正本  README 是唯一描述正本；仓库里不许有商店描述文档；三处正文能从它现出

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


# 描述：**README 是唯一正本**，三处商店正文是从它现出的派生视图，**不进仓库**。
#
# 用户 2026-09-21 的原话：「别在仓库 docs 里放描述文档了，我上传商店是一定会微调的，到时候又不统一」——
# 商店页面是在平台编辑器里改的，仓库再存一份就必然分家，而且分家看不出来。
# 所以这里检查的不再是"产物与正本一致"，而是三件更难糊弄的事：
#   ① 仓库里确实没有描述文档（规则进闸，否则下一轮又会有人顺手加回来）；
#   ② 两份 README 还得对得上（双语对齐是派生能成立的前提）；
#   ③ 三处正文真的现得出来，且各自的平台规矩成立（md 有头图/徽章；百科是纯文本、无空行）。
DESCRIPTION_EXCEPTIONS = ("README.md", "README_EN.md")


def readme_headings(path: Path) -> list[str]:
    return [m.group(1) for line in path.read_text(encoding="utf-8").splitlines()
            if (m := re.match(r"^##\s+(.+?)\s*$", line))]


def check_description_pipeline() -> None:
    """描述只有一处正本（README），三处商店正文现出现用。"""
    # ---- ① 仓库里不许有描述文档 ----
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or ".git" in path.parts:
            continue
        if "description" in path.name.lower() and path.name not in DESCRIPTION_EXCEPTIONS:
            fail(f"{path.relative_to(ROOT).as_posix()} —— 仓库里不留商店描述文档："
                 f"正文一律从 README 现出（python tools/store_copy.py --platform …）")
    if (ROOT / "docs" / "store").exists():
        fail("docs/store/ 还在 —— 商店正文不落库（README 就是正本），删掉它")

    sys.path.insert(0, str(Path(__file__).resolve().parent))
    try:
        import store_copy
    except ImportError as exc:  # pragma: no cover
        fail(f"读不到 tools/store_copy.py（{exc}）—— 商店正文由它从 README 现出")
        return

    # ---- ② 两份 README 要对得上 ----
    zh_head = readme_headings(ROOT / "README.md")
    en_head = readme_headings(ROOT / "README_EN.md")
    if len(zh_head) != len(en_head):
        fail(f"两份 README 的节数不等：README.md {len(zh_head)} 节 vs README_EN.md {len(en_head)} 节 "
             f"—— 双语对齐是商店正文能派生的前提")
    stats["readme_sections"] = len(zh_head)

    # 映射表里每个节名都得真的存在：不然改个 README 节名，商店正文会**静默少一节**
    for lang, pairs in store_copy.MAP.items():
        have = set(readme_headings(store_copy.SOURCES[lang]))
        for readme_title, _ in pairs:
            if readme_title not in have:
                fail(f"{store_copy.SOURCES[lang].name} 里没有「{readme_title}」这一节 —— "
                     f"商店正文的映射表（tools/store_copy.py 的 MAP）要跟着改名一起改")

    # ---- ③ 三处正文现得出来，且各自的平台规矩成立 ----
    for platform in sorted(store_copy.PLATFORMS):
        try:
            text = store_copy.render(platform)
        except SystemExit as exc:
            fail(f"{platform} 正文现不出来：{exc}")
            continue
        if len(text.strip()) < 200:
            fail(f"{platform} 正文只有 {len(text.strip())} 字符 —— 现出来的东西不像正文")
            continue
        stats["platforms"] = stats.get("platforms", 0) + 1

        if platform == "mcmod":
            # 百科的编辑器不解析 Markdown，而且会吃掉空行 —— 两条都验过会红
            for pattern, what in ((r"\*\*", "粗体标记 `**`"), (r"`", "反引号代码"),
                                  (r"\]\(", "Markdown 链接"), (r"(?m)^\s*[-*]\s", "列表标记")):
                if re.search(pattern, text):
                    fail(f"mcmod 正文里出现了{what} —— 百科的编辑器不认 Markdown，"
                         f"渲染时要用纯文本（tools/store_copy.py 的 mcmod_plain）")
            if any(not line.strip() for line in text.splitlines()):
                fail("mcmod 正文里有空行 —— 百科的编辑器会吃掉空行、把条目并成一段，"
                     "所以那份一行就是一条，不留空行")

    # 门面与商店的头图必须是同一张（附件 URL：raw 域名在用户网络下拉不动）
    readme = ROOT / "README.md"
    if readme.is_file():
        readme_image = first_image_url(readme.read_text(encoding="utf-8"))
        if readme_image and readme_image != store_copy.BANNER_URL:
            fail(f"README 顶部那张图（{readme_image}）与商店正文头图（{store_copy.BANNER_URL}）"
                 f"不是同一张 —— 门面与商店各用各的图，就没有东西会把它们对起来")
    if not (ROOT / store_copy.BANNER).is_file():
        fail(f"仓库里没有横幅正本 {store_copy.BANNER} —— 对外用的是附件 URL，"
             f"正本丢了就再也没法重新上传同一张图")


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
    check_description_pipeline()

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
          f"描述正本 README ×2（{stats.get('readme_sections', 0)} 节/份）→ 商店正文 "
          f"{stats.get('platforms', 0)} 处现出现用）")
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
