#!/usr/bin/env python3
"""产物级断言：看 jar **里面**，不信"构建成功"这四个字。

单测跑的是类路径、结构闸盯的是源码目录与 AP 输出 —— 两者都拦不住"打包这一步少塞了一个
嵌套 jar""native 没进来""构建戳说的是别的版本"。这几条规则原本只在动打包脚本时手跑，
现在 CI 每次构建都跑一遍（本地也能跑，一个命令）。

用法：python tools/verify_jars.py platforms/1.20.1-forge
退出码 0 = 全绿，1 = 有问题（问题打在 stderr）。
"""

from __future__ import annotations

import io
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# 与 build.gradle 里 jarJar 的版本区间必须一致：钉死单元素区间，否则会解析到别的版本
# （错版本 = 首次调用 UnsatisfiedLinkError，见 docs/architecture.md 的「打包」一节）
NANOVG_VERSIONS = {"Fabric": "3.3.1", "Forge": "3.3.1", "NeoForge": "3.3.3"}
# Trellis（dev.e33.trellis）与 gradle 的 jarJar 区间 / third_party 提交的文件名三处同源这一份
TRELLIS_VERSION = "0.1.0-SNAPSHOT"

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


def read_root_identity(key: str) -> str:
    """读仓库根的身份值。读到的东西拿来跟 jar 里的逐字对照 —— 这是"身份真的进了产物"的落点。"""
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    match = re.search(rf"^{re.escape(key)}\s*=\s*(.*?)\s*$", text, re.MULTILINE)
    if not match:
        fail(f"仓库根 gradle.properties 里找不到 {key}")
        return ""
    return match.group(1)


def read_root_version() -> str:
    return read_root_identity("mod_version") or "?"


def main() -> int:
    project = sys.argv[1] if len(sys.argv) > 1 else "platforms/1.20.1-forge"
    targets = json.loads((ROOT / "versions" / "targets.json").read_text(encoding="utf-8"))
    target = next((v for v in targets.values() if isinstance(v, dict) and v.get("project") == project), None)
    if target is None or target["loader"] not in NANOVG_VERSIONS:
        fail(f"未知打包目标 {project}")
        return report()
    forge = target["loader"] == "Forge"
    fabric = target["loader"] == "Fabric"
    nvg_version = NANOVG_VERSIONS[target["loader"]]
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

        # ForgeGradle also emits an undeclared LWJGL core jar. NeoForge uses its own core directly.
        core_jar = f"META-INF/jarjar/lwjgl-{nvg_version}.jar"
        if forge and core_jar not in names:
            fail(f"缺少 ForgeGradle 的 {core_jar}")
        if not forge and core_jar in names:
            fail(f"{target['loader']} 包重复嵌入了 Minecraft 自带的 LWJGL：{core_jar}")

        required_libraries = {
            ("org.lwjgl", "lwjgl-nanovg"): nvg_version,
            ("dev.e33.trellis", "core"): TRELLIS_VERSION,
            ("dev.e33.trellis", "nvg"): TRELLIS_VERSION,
        }

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
        if (forge or fabric) and not any(n.endswith(".refmap.json") for n in names):
            fail("缺少 mixin refmap，运行时无法定位拾取注入点")

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

        # 9. 作者署名里带中文，而 MANIFEST 是另一条编码路径。Gradle 按 UTF-8 写，但这条
        # 一旦漂了，表现是 mod 列表/jar 信息里一串乱码 —— 那种错没人会去查构建。
        if "META-INF/MANIFEST.MF" in names:
            manifest = zf.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
            authors = read_root_identity("mod_authors")
            if authors and authors not in manifest:
                fail(f"MANIFEST.MF 里的 Vendor 对不上 mod_authors（{authors}）—— "
                     f"署名没写进去，或者编码写成了非 UTF-8")

        if fabric:
            check_fabric(zf, names, nvg_version, target["minecraft"])
            print(f"verify_jars: {jar.name}（{len(names)} 个条目）")
            return report()

        # 8. 身份必须真的展开进产物，而且要跟仓库根一字不差。
        # 【为什么这条要有】processResources 的 expand 是"缺键就抛"，但**值写错**它一声不响：
        # 作者写错、仓库地址还指着旧账号、致谢漏了某个组件 —— 全都会打出一个构建绿灯的 jar，
        # 而拿到它的人只会在 mod 列表里看到错的东西（pointing 一个打不开的链接）。
        toml_name = "META-INF/mods.toml" if forge else "META-INF/neoforge.mods.toml"
        if toml_name not in names:
            fail(f"jar 里缺少 {toml_name}")
        else:
            toml = zf.read(toml_name).decode("utf-8", "replace")
            if "${" in toml:
                fail("mods.toml 里还有没展开的 ${...} —— 身份没进产物")
            if not forge and 'config = "pickupcard.mixins.json"' not in toml:
                fail("neoforge.mods.toml 没有注册拾取 Mixin")
            authors = read_root_identity("mod_authors")
            repo_url = read_root_identity("mod_repo_url")
            for key, needle in (
                ("mod_authors", f'authors = "{authors}"' if authors else ""),
                ("mod_repo_url", f'displayURL = "{repo_url}"' if repo_url else ""),
                ("mod_repo_url", f'issueTrackerURL = "{repo_url}/issues"' if repo_url else ""),
                ("mod_license", f'license = "{read_root_identity("mod_license")}"'
                 if read_root_identity("mod_license") else ""),
                ("mod_credits", read_root_identity("mod_credits")),
                ("mod_description", read_root_identity("mod_description")),
            ):
                if needle and needle not in toml:
                    fail(f"jar 里的 mods.toml 对不上仓库根 {key} —— 找不到 {needle!r}")

        # 10. JarJar metadata 必须如实：解析得了、每个嵌套 jar 都有条目、每个条目的文件都在、
        # schema 关键字段（path/artifactVersion）非空。2026-09-25 两次真机 NoClassDefFoundError
        # （手拼 schema 错字段 / deploy 脚本丢 nanovg 嵌套）全发生在"嵌套文件在但 metadata 说错"
        # 或反过来 —— 文件数对不上≠条目对得上，所以两边都查。
        meta_name = "META-INF/jarjar/metadata.json"
        if meta_name not in names:
            fail("缺少 META-INF/jarjar/metadata.json")
        else:
            try:
                meta = json.loads(zf.read(meta_name))
                entries = meta.get("jars", [])
                declared = set()
                for j in entries:
                    path = j.get("path", "")
                    declared.add(path)
                    if not path or not j.get("version", {}).get("artifactVersion"):
                        fail(f"JarJar metadata 条目缺关键字段（path/artifactVersion）：{j}")
                    elif path not in names:
                        fail(f"JarJar metadata 声明了 {path}，但 jar 里没有这个文件")
                nested_jars = {n for n in names
                               if n.startswith("META-INF/jarjar/") and n.endswith(".jar")}
                allowed_undeclared = {core_jar} if forge else set()
                for path in sorted(nested_jars - declared - allowed_undeclared):
                    fail(f"嵌套 jar {path} 没有对应的 metadata 条目")
                for (group, artifact), version in required_libraries.items():
                    path = f"META-INF/jarjar/{artifact}-{version}.jar"
                    if not any(j.get("identifier") == {"group": group, "artifact": artifact}
                               and j.get("path") == path
                               and j.get("version", {}).get("artifactVersion") == version
                               for j in entries):
                        fail(f"JarJar metadata 里没有正确声明 {group}:{artifact}:{version}")
            except ValueError as e:
                fail(f"JarJar metadata.json 解析失败：{e}")

    print(f"verify_jars: {jar.name}（{len(names)} 个条目）")
    return report()


def check_fabric(zf: zipfile.ZipFile, names: set[str], nvg_version: str, minecraft: str) -> None:
    metadata = read_json(zf, "fabric.mod.json")
    if metadata is None:
        return
    repo_url = read_root_identity("mod_repo_url")
    expected = {
        "id": read_root_identity("mod_id"),
        "version": read_root_version(),
        "name": read_root_identity("mod_name"),
        "description": read_root_identity("mod_description"),
        "authors": [read_root_identity("mod_authors")],
        "contributors": [read_root_identity("mod_credits")],
        "contact": {"homepage": repo_url, "sources": repo_url, "issues": f"{repo_url}/issues"},
        "license": read_root_identity("mod_license"),
        "icon": "logo.png",
        "environment": "client",
    }
    for key, value in expected.items():
        if metadata.get(key) != value:
            fail(f"fabric.mod.json 的 {key} 应为 {value!r}")

    entrypoints = metadata.get("entrypoints", {})
    for name, entry in (
        ("client", "com.niuqu.pickupcard.PickupCard"),
        ("modmenu", "com.niuqu.pickupcard.client.PickupCardModMenu"),
    ):
        if entry not in entrypoints.get(name, []) or entry.replace(".", "/") + ".class" not in names:
            fail(f"Fabric 缺少 {name} 入口 {entry}")

    depends = metadata.get("depends", {})
    if depends.get("minecraft") != minecraft or not {"fabricloader", "fabric-api", "forgeconfigapiport"} <= depends.keys():
        fail("fabric.mod.json 缺少 Minecraft 版本或 Fabric 运行依赖")

    if "pickupcard.mixins.json" not in metadata.get("mixins", []):
        fail("fabric.mod.json 未注册拾取 Mixin")
    access_widener = metadata.get("accessWidener")
    if access_widener not in names or zf.read(access_widener).split(b"\n", 1)[0].split() != [
        b"accessWidener", b"v2", b"intermediary"
    ]:
        fail("Fabric 渲染访问声明缺失或未转换到 Intermediary")

    mixin = read_json(zf, "pickupcard.mixins.json")
    if mixin is not None:
        mixin_class = "com/niuqu/pickupcard/mixin/ClientPacketListenerMixin"
        if ("ClientPacketListenerMixin" not in mixin.get("client", [])
                or mixin_class + ".class" not in names):
            fail("Fabric 包缺少客户端拾取 Mixin")
        refmap = read_json(zf, mixin.get("refmap", ""))
        if refmap is not None:
            mapping = refmap.get("mappings", {}).get(mixin_class, {})
            for method in ("handleTakeItemEntity", "handleSetEntityData", "handleContainerSetSlot", "handleContainerContent"):
                if not mapping.get(method, "").startswith("Lnet/minecraft/class_634;method_"):
                    fail(f"Fabric refmap 缺少拾取注入点 {method}")
            for method, owner in (("playLocalSound", "class_638"), ("ensureRunningOnSameThread", "class_2600")):
                if not any(f";{method}(" in source and target.startswith(f"Lnet/minecraft/{owner};method_")
                           for source, target in mapping.items()):
                    fail(f"Fabric refmap 缺少调用注入点 {method}")

    nested = {
        f"META-INF/jars/lwjgl-nanovg-{nvg_version}.jar": "org/lwjgl/nanovg/NanoVG.class",
        f"META-INF/jars/core-{TRELLIS_VERSION}.jar": "dev/e33/trellis/geom/Insets.class",
        f"META-INF/jars/nvg-{TRELLIS_VERSION}.jar": "dev/e33/trellis/render/nanovg/NvgCanvas.class",
    }
    actual = {path for path in names if path.startswith("META-INF/jars/") and path.endswith(".jar")}
    declared = {item.get("file") for item in metadata.get("jars", [])}
    if set(nested) != actual or set(nested) != declared:
        fail(f"Fabric 嵌套库不一致，声明={sorted(str(path) for path in declared)}，文件={sorted(actual)}")
    for path, expected_class in nested.items():
        if path not in actual:
            continue
        try:
            with zipfile.ZipFile(io.BytesIO(zf.read(path))) as library:
                if not {"fabric.mod.json", expected_class} <= set(library.namelist()):
                    fail(f"Fabric 嵌套库缺少元数据或绑定类：{path}")
        except zipfile.BadZipFile:
            fail(f"Fabric 嵌套库损坏：{path}")


def read_json(zf: zipfile.ZipFile, name: str) -> dict | None:
    try:
        return json.loads(zf.read(name))
    except (KeyError, ValueError) as error:
        fail(f"无法读取 {name!r}：{error}")
        return None


def report() -> int:
    if problems:
        print(f"verify_jars: {len(problems)} 个问题", file=sys.stderr)
        for p in problems:
            print(f"  ✗ {p}", file=sys.stderr)
        return 1
    print("  全绿：无摊平类残留 / 嵌套成对同版 / 四平台 native 在主 jar / 构建戳与身份一致 / "
          "Mixin 配置与目标匹配 / 许可随产物 / 图标与设计正本一致 / 加载器元数据正确")
    return 0


if __name__ == "__main__":
    sys.exit(main())
