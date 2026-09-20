# design/ — 主题参数的正本与 mod 图标

这个目录只有两样东西：**参数正本**，和**由它生成的 mod 图标**。

## 文件

| 文件 | 用途 |
| --- | --- |
| `tokens.css` | **全部可调参数的唯一定义处**。`tools/css_tokens.py` 把它编译成 `assets/pickupcard/styles/*.json`，Java 读那份 JSON。改主题只动这里，不手改 JSON。 |
| `icon.py` | 生成 mod 图标。配色与几何**从 `tokens.css` 读** —— 改主题后重跑一次，图标跟着变；手画的那张不会，漂了也没人看得出来。 |
| `icon.png` | 图标正本（128×128）。构建时由 `processResources` 改名成 `logo.png` 进 jar（`mods.toml` 的 `logoFile` 指着它），**资源目录里没有第二份**；`tools/verify_jars.py` 盯着两者逐字节一致。 |

## 用法

```bash
python tools/css_tokens.py            # tokens.css -> assets/pickupcard/styles/*.json
python tools/css_tokens.py --check    # 只校验不写盘（改完 tokens.css 忘了重跑，这里会红）
python design/icon.py                 # 出 design/icon.png（128×128）
python design/icon.py --size 512      # 商店用的那份，不入库
```

## 规矩

- `tokens.css` 的 `:root` / `:root[data-theme=...]` 里**只放标量 token**，那是唯一会被
  `tools/css_tokens.py` 抽取的部分。清单外的自定义属性 → 抽取脚本**直接失败退出**。
  （静默失败最毒：不认识的属性被悄悄忽略，改十版设计都不知道为什么没变化。）
- **几何与文字宽度一律不进转换**，就地写死。卡宽在游戏里跟名字走，算不准。
