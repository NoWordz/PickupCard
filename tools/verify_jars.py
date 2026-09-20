#!/usr/bin/env python3
"""产物级断言：看 jar **里面**，不信"构建成功"这四个字。

单测跑的是类路径、结构闸盯的是源码目录与 AP 输出 —— 两者都拦不住"打包这一步少塞了一个
嵌套 jar""native 没进来""构建戳说的是别的版本"。这几条规则原本只在动打包脚本时手跑，
现在 CI 每次构建都跑一遍（本地也能跑，一个命令）。

用法：python tools/verify_jars.py platforms/1.20.1-forge
退出码 0 = 全绿，1 = 有问题（问题打在 stderr）。
"""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# 与 build.gradle 里 jarJar 的版本区间必须一致：钉死单元素区间，否则会解析到别的版本
# （错版本 = 首次调用 UnsatisfiedLinkError，见 docs/architecture.md 的「打包」一节）
NANOVG_VERSION = "3.3.1"

# 四平台 native 必须留在主 jar：它们是资源不是类，不产生 JPMS 包导出
NATIVE_FILES = [
    "windows/x64/org/lwjgl/nanovg/lwjgl_nanovg.dll",
    "linux/x64/org/lwjgl/nanovg/liblwjgl_nanovg.so",
    "macos/x64/org/lwjgl/nanovg/liblwjgl_nanovg.dylib",
    "macos/arm64/org/lwjgl/nanovg/liblwjgl_nanovg.dylib",
]

problems: list[str] = []


def fail(msg: str) -> None:
    problems.append(msg)


def read_root_version() -> str:
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    match = re.search(r"^mod_version\s*=\s*(\S+)\s*$", text, re.MULTILINE)
    if not match:
        fail("仓库根 gradle.properties 里找不到 mod_version")
        return "?"
    return match.group(1)


def main() -> int:
    project = sys.argv[1] if len(sys.argv) > 1 else "platforms/1.20.1-forge"
    libs = ROOT / project / "build" / "libs"
    if not libs.is_dir():
        print(f"verify_jars: {libs} 不存在 —— 先构建", file=sys.stderr)
        return 1

    jars = [p for p in sorted(libs.glob("*.jar"))
            if "-slim" not in p.name and "sources" not in p.name]
    if len(jars) != 1:
        fail(f"期望恰好一个发布 jar，实际 {len(jars)} 个：{[p.name for p in jars]}")
        report()
        return 1
    jar = jars[0]

    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())

        # 1. 主 jar 里不许有摊平的 nanovg 绑定类（有 = 与 UI Deck 同装会让 JPMS 拒绝启动）
        leaked = [n for n in names if n.startswith("org/lwjgl/nanovg/") and n.endswith(".class")]
        if leaked:
            fail(f"主 jar 里有 {len(leaked)} 个摊平的 org/lwjgl/nanovg 类（例：{leaked[0]}）"
                 f" —— 与 UI Deck 同装会 JPMS ResolutionException")

        # 2. JarJar 嵌套必须成对且同版
        for nested in (f"META-INF/jarjar/lwjgl-{NANOVG_VERSION}.jar",
                       f"META-INF/jarjar/lwjgl-nanovg-{NANOVG_VERSION}.jar"):
            if nested not in names:
                fail(f"缺少 JarJar 嵌套产物 {nested}")

        # 3. 四平台 native 都还在主 jar 里
        for native in NATIVE_FILES:
            if native not in names:
                fail(f"缺少 native：{native}")

        # 4. 构建戳必须存在，且版本与仓库根身份一致
        stamp_name = "pickupcard-build.properties"
        if stamp_name not in names:
            fail(f"缺少构建戳 {stamp_name} —— 拿到 jar 的人就没法判断这些字节来自哪次提交")
        elif read_root_version() not in zf.read(stamp_name).decode("utf-8", "replace"):
            expected = read_root_version()
            fail(f"构建戳里的版本与仓库根 mod_version={expected} 对不上")

        # 5. mixin 配置与 refmap 都要在（缺了 = mixin 不加载、拾取事件整个哑掉）
        if "pickupcard.mixins.json" not in names:
            fail("缺少 pickupcard.mixins.json")
        if not any(n.endswith(".refmap.json") for n in names):
            fail("缺少 mixin refmap（client 侧 SRG 名对不上，运行时才炸）")

        # 6. 许可与第三方声明要随产物走（宽松许可的条件就是把声明带上）
        for doc in ("LICENSE", "THIRD_PARTY_NOTICES.md"):
            if doc not in names:
                fail(f"缺少 {doc} —— 它在仓库根有正本，由 processResources 拷进 jar")

        # 7. mod 图标：jar 里的 logo.png 由 design/icon.png 改名而来，必须逐字节一致 ——
        # "重跑图标脚本之后忘了重新构建"会让 jar 里躺着旧图标，而 mods.toml 照样指着它。
        icon = ROOT / "design" / "icon.png"
        if icon.is_file():
            if "logo.png" not in names:
                fail("jar 里没有 logo.png，而 mods.toml 的 logoFile 指着它")
            elif zf.read("logo.png") != icon.read_bytes():
                fail("jar 里的 logo.png 与 design/icon.png 不一致 —— 重跑 design/icon.py 之后要重新构建")

    print(f"verify_jars: {jar.name}（{len(names)} 个条目）")
    return report()


def report() -> int:
    if problems:
        print(f"verify_jars: {len(problems)} 个问题", file=sys.stderr)
        for p in problems:
            print(f"  ✗ {p}", file=sys.stderr)
        return 1
    print("  全绿：无摊平类残留 / 嵌套成对同版 / 四平台 native 在主 jar / 构建戳与身份一致 / "
          "mixin 与 refmap 齐 / 许可随产物 / 图标与设计正本一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
