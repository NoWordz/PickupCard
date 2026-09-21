# design/ — 主题参数的正本、项目 logo 与横幅，和 mod 图标

这个目录只有四样东西：**参数正本**、**项目 logo**、**项目横幅**，和**由 logo 派生的 mod 图标**。

## 文件

| 文件 | 用途 |
| --- | --- |
| `tokens.css` | **全部可调参数的唯一定义处**。`tools/css_tokens.py` 把它编译成 `assets/pickupcard/styles/*.json`，Java 读那份 JSON。改主题只动这里，不手改 JSON。 |
| `logo.png` | **项目 logo**（512×512，卡片堆 + 镐子）。GitHub 仓库头像/社交预览、商店图标用的就是它。 |
| `banner.png` | **项目横幅**（512×257 像素字 wordmark）。README 顶图与两份商店正文的头图都用它 —— README 写相对路径，正文写 GitHub raw 绝对 URL（正文没有基准路径）。 |
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
- **横幅是手工资产，不经脚本派生**：换就整份换 `banner.png`，然后确认 README 与两份商店正文
  三处指的还是它（`tools/verify_targets.py` 会比对三处的同一性）。
  别为商店另外生成一张 —— 两处分家之后，没有任何闸盯得住商店后台里那张图。
