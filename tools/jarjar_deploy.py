#!/usr/bin/env python
"""把 slim 发布 jar 构造成 JarJar 部署 jar（嵌套 Trellis core/nvg）。

【为什么这个脚本存在】pickupcard 的发布物是 slim jar（不含 Trellis），生产环境靠
Forge 的 jar-in-jar 把 Trellis 两颗库挂成独立模块。构造必须走这个脚本，**不能手拼
metadata** —— 2026-09-25 手拼漏了 `path`/`artifactVersion` 字段名（写成 artifact），
Forge MetadataIOHandler 解析 NPE、嵌套全没挂上，真机 NoClassDefFoundError 崩溃
（错误报告-2026-9-25_17.42.07）。

【字段 schema 是 javap 出来的，不是猜的】JarJarMetadata 0.3.19 的
ContainedJarMetadataSerializer 读：identifier(group/artifact)、version、path、
isObfuscated；ContainedVersionSerializer 读：range、artifactVersion。

用法：
  python tools/jarjar_deploy.py <slim.jar> <core.jar> <nvg.jar> <输出.jar>
然后手动拷到测试实例 mods/（或接进发版链）。
"""
import json
import shutil
import sys
import zipfile


def main():
    if len(sys.argv) != 5:
        print(__doc__)
        sys.exit(1)
    slim, core, nvg, dst = sys.argv[1:5]
    shutil.copy(slim, dst)

    nests = [
        (core, "trellis-core-0.1.0-SNAPSHOT.jar", "trellis-core"),
        (nvg, "trellis-nvg-0.1.0-SNAPSHOT.jar", "trellis-nvg"),
    ]
    meta = {"jars": [
        {
            "identifier": {"group": "dev.e33", "artifact": artifact},
            "version": {"range": "[0.1.0-SNAPSHOT]", "artifactVersion": "0.1.0-SNAPSHOT"},
            "path": "META-INF/jarjar/" + name,
            "isObfuscated": False,
        }
        for _, name, artifact in nests
    ]}

    with zipfile.ZipFile(dst, "a", zipfile.ZIP_DEFLATED) as z:
        for path, name, _artifact in nests:
            z.write(path, "META-INF/jarjar/" + name)

    tmp = dst + ".repack"
    with zipfile.ZipFile(dst) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for n in zin.namelist():
            if n == "META-INF/jarjar/metadata.json":
                continue
            zout.writestr(n, zin.read(n))
        zout.writestr("META-INF/jarjar/metadata.json", json.dumps(meta, indent=1))
    shutil.move(tmp, dst)

    with zipfile.ZipFile(dst) as z:
        m = json.loads(z.read("META-INF/jarjar/metadata.json"))
        nested = [n for n in z.namelist() if n.startswith("META-INF/jarjar/") and n.endswith(".jar")]
        assert len(m["jars"]) == 2 and len(nested) == 2, "JarJar 构造不完整"
        for j in m["jars"]:
            assert j["path"] and j["version"]["artifactVersion"], "schema 字段缺失"
    print("ok:", dst)


if __name__ == "__main__":
    main()
