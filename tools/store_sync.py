#!/usr/bin/env python3
"""把 Modrinth 项目正文同步成"从 README 现出的那一份"。

【正本是 README】描述只有一个地方要改：`README.md` / `README_EN.md`。三处商店正文都是
从它现出的派生视图（`tools/store_copy.py`），**仓库里不留描述文档** —— 商店页面是在平台编辑器里
改的，仓库再存一份就必然分家（用户 2026-09-21：「别在仓库 docs 里放描述文档了，我上传商店是一定会微调的」）。
规范见 skill `store-description`；闸是 `python tools/verify_targets.py`（含"仓库里不许有描述文档"）。

【为什么只有 Modrinth】CurseForge 的官方 Upload API 只有"上传文件"的端点，**没有改项目
描述的端点** —— 那边只能人工贴，脚本不去假装能做（AtomChat 那边查证过同一件事）。

【默认演练】不带参数时只拉远端正文、比差异、把结果打出来，一个字节都不改；加 `--apply`
才真的 PATCH。这也是它在 CI 里默认安全的原因。

用法：
  python tools/store_sync.py            # 演练：看差异
  python tools/store_sync.py --apply    # 真同步（需要 MODRINTH_TOKEN）
退出码：0 = 一致或已同步；1 = 出错；2 = 演练发现差异（便于 CI/本地一眼分辨）。
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = "https://api.modrinth.com/v2"
sys.path.insert(0, str(Path(__file__).resolve().parent))


def local_body() -> str:
    """本地这份 = 从 README 现出的正文（唯一正本是 README，仓库里不留描述文档）。"""
    import store_copy
    return store_copy.render("modrinth").strip()


def project_id() -> str:
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    match = re.search(r"^modrinth_project_id\s*=\s*(\S+)\s*$", text, re.MULTILINE)
    value = match.group(1) if match else ""
    if not value or value == "UNSET":
        print("store_sync: gradle.properties 里的 modrinth_project_id 还是 UNSET —— "
              "先在 Modrinth 上建项目、把 id 填回去，再跑这个脚本", file=sys.stderr)
        sys.exit(1)
    return value


def request(method: str, url: str, payload: dict | None = None) -> dict:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    # Modrinth 要求带一个能识别调用方的 User-Agent，缺了会被 403。
    req.add_header("User-Agent", "E33EPUS/PickupCard store_sync (github.com/E33EPUS/PickupCard)")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    token = os.environ.get("MODRINTH_TOKEN")
    if token:
        req.add_header("Authorization", token)
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = resp.read().decode("utf-8")
    return json.loads(body) if body.strip() else {}


def main() -> int:
    apply = "--apply" in sys.argv[1:]
    local = local_body()
    pid = project_id()

    remote = request("GET", f"{API}/project/{pid}")
    remote_body = (remote.get("body") or "").strip()
    print(f"store_sync: 项目 {pid}（{remote.get('title')}）")
    print(f"  本地（README 现出）: {len(local.splitlines())} 行")
    print(f"  远端正文                        : {len(remote_body.splitlines())} 行")

    if local == remote_body:
        print("  一致 —— 不需要同步")
        return 0

    if not apply:
        print("  有差异（演练模式，什么都没改）。加 --apply 才真的推上去。")
        return 2

    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        print("store_sync: --apply 需要 MODRINTH_TOKEN（PATCH 是要鉴权的）", file=sys.stderr)
        return 1
    # 只改 body 这一个字段 —— PATCH 的语义就是"只动你给的字段"，不碰标题/图标/分类。
    request("PATCH", f"{API}/project/{pid}", {"body": local})
    print("  已同步（只改了 body 字段）")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except urllib.error.HTTPError as e:
        print(f"store_sync: HTTP {e.code} —— {e.read().decode('utf-8', 'replace')[:300]}",
              file=sys.stderr)
        sys.exit(1)
    except urllib.error.URLError as e:
        print(f"store_sync: 连不上 Modrinth（{e.reason}）", file=sys.stderr)
        sys.exit(1)
