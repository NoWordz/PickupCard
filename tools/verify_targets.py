#!/usr/bin/env python3
"""校验多目标仓库的结构是否自洽。

这套结构的价值全在"声明得出来、却编不过去的那种矛盾，要在 CI 就红"。
这个脚本就是那道闸。它读版本声明（不读文档、不读注释），把互相矛盾的地方找出来。

检查项：

  1. 矩阵是规则     每个 Minecraft 版本都要有 Fabric / NeoForge / Forge 三条（缺了要显式记 buildable:false）
  2. 身份只有一份   仓库根 gradle.properties 必须有全部身份键
  3. 工程与条目互存 工程目录存在 → 矩阵里必须有它；矩阵里的 project → 目录必须存在
  4. 谓词与挂载一致 挂了版本层就必须够得到那个版本；加载器层/映射层同理
  5. 层目录由声明   层的目录路径必须能从它钉的轴推出来
  6. populated 如实 声明 populated:true 的层不能是空的；声明 false 的不能有内容
  7. 平台不许定义身份  各平台 gradle.properties 里不许出现身份键（否则就是第二个真源）
  8. buildable 不许有未验证字段

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
    "mod_authors", "mod_description", "mod_version",
]

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


def main() -> int:
    raw_targets = json.loads((ROOT / "versions/targets.json").read_text(encoding="utf-8"))
    targets = entries(raw_targets)
    layers = entries(json.loads((ROOT / "versions/layers.json").read_text(encoding="utf-8")))

    # ---- 1. 身份只有一份 ----
    root_props = read_properties(ROOT / "gradle.properties")
    for key in IDENTITY_KEYS:
        if key not in root_props:
            fail(f"仓库根 gradle.properties 缺身份键 {key}")

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

    print(f"verify_targets: 全绿（{len(targets)} 个目标，{len(layers)} 个层，约 {total_checks} 项检查）")
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
