# design/ — 主题参数的正本、项目 logo 与横幅，和 mod 图标

这个目录只有四样东西：**参数正本**、**项目 logo**、**项目横幅**，和**由 logo 派生的 mod 图标**。

## 文件

| 文件 | 用途 |
| --- | --- |
| `tokens.css` | **全部可调参数的唯一定义处**。`tools/css_tokens.py` 把它编译成 `assets/pickupcard/styles/*.json`，Java 读那份 JSON。改主题只动这里，不手改 JSON。 |
| `logo.png` | **项目 logo**（512×512，卡片堆 + 镐子）。GitHub 仓库头像/社交预览、商店图标用的就是它。 |
| `banner.png` | **项目横幅**（512×257 像素字 wordmark）的**正本**。README 顶图与两份商店正文的头图在**引用上**用的是它的 GitHub 附件 URL（见下），这份文件是"附件没了还能再传一次"的那个备份。 |
| `icon.py` | 从 `logo.png` 派生 mod 图标：**裁到主体**（含微光）再缩到目标尺寸。 |
| `icon.png` | mod 图标（128×128，由 `icon.py` 产出）。构建时由 `processResources` 改名成 `logo.png` 进 jar（`mods.toml` 的 `logoFile` 指着它），**资源目录里没有第二份**；`tools/verify_jars.py` 盯着两者逐字节一致。 |

## 用法

```bash
python tools/css_tokens.py            # tokens.css -> assets/pickupcard/styles/*.json
python tools/css_tokens.py --check    # 只校验不写盘（改完 tokens.css 忘了重跑，这里会红）
python design/icon.py                 # logo.png -> design/icon.png（128×128）
python design/icon.py --size 256      # 更大的那份，不入库
python design/icon.py --print-box     # 只打印裁切框，看脚本认出的主体对不对
```

## 规矩

- `tokens.css` 的 `:root` / `:root[data-theme=...]` 里**只放标量 token**，那是唯一会被
  `tools/css_tokens.py` 抽取的部分。清单外的自定义属性 → 抽取脚本**直接失败退出**。
  （静默失败最毒：不认识的属性被悄悄忽略，改十版设计都不知道为什么没变化。）
- **几何与文字宽度一律不进转换**，就地写死。卡宽在游戏里跟名字走，算不准。
- **图标不再随主题配色变**（2026-09-21 起）：正本是手画的 `logo.png`，主题配色归卡面。
  代价是"改主题图标自动跟着变"这条性质没有了 —— 换来的是项目 logo 认得出来。
  仍然保留的是**一键重来**：`icon.py` 一条命令重出，`verify_jars.py` 再盯着 jar 里那份
  与它逐字节一致（图标是唯一没有其它门禁的视觉资产，这两条不能一起丢）。
- **换 logo 就是换 `logo.png` 然后重跑 `icon.py`**：别手改 `icon.png`，那会让"正本 → 派生"
  这条链断掉，而下一次重跑会把手改的那份覆盖掉。
- **横幅是手工资产，不经脚本派生**，而且**对外用的是 GitHub 附件 URL**（`tools/store_copy.py` 的 `BANNER_URL`）。
  为什么不用仓库里的相对/raw 链接：2026-09-21 实测 —— raw.githubusercontent.com 在用户网络下拉不动，
  而 README 写相对路径也没用，GitHub 渲染时会把相对图片路径重写成 raw 域名，于是门面与商店一起坏。
  **换横幅 = 三件事**：换 `banner.png` → 在 GitHub 上传拿到新的附件 URL → 改 `BANNER_URL`（README 两份由闸盯着必须与它一致）。
  长期更好的一档：Modrinth 项目建好后把图传进图库，换成 `cdn.modrinth.com` 的地址（AtomChat 就是这么做的，那个 CDN 在用户网络下也通）。
