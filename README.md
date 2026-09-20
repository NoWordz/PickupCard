[简体中文](README.md) | [English](README_EN.md)

<h1 align="center">Pickup Card</h1>

<p align="center">
  <em>捡到的每一样东西，都变成 HUD 上的一张卡</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.20.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge-red">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B-yellow">
  <img alt="Version" src="https://img.shields.io/github/v/release/E33EPUS/PickupCard?sort=semver">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

<p align="center">
  <a href="https://github.com/E33EPUS/PickupCard/actions/workflows/build.yml"><img alt="Build" src="https://github.com/E33EPUS/PickupCard/actions/workflows/build.yml/badge.svg?branch=main"></a>
</p>

> 最新版见 [Releases](https://github.com/E33EPUS/PickupCard/releases) · [更新日志](CHANGELOG.md) · 纯客户端，零必需前置。

## 这是什么

捡起物品或经验时，屏幕右下角弹出一张玻璃拟态风格的卡片：物品图标、名字与本次进账数量，稀有度决定强调色。连捡同一样东西不会刷屏，而是并进同一张卡、数字弹一下往上滚。

界面全部由 [NanoVG](https://github.com/memononen/nanovg) 矢量自绘，不依赖原版提示纹理，也不依赖任何渲染前置。模组是**纯客户端**的：不装服务端也能用，任何服务器、任何整合包都能进。

## 安装

| 依赖 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x（ForgeGradle 6 线） |
| 其他前置 | **无** |
| 可选联动 | [RarityCore](https://modrinth.com/mod/raritycore)：装了就用它的七档稀有度与自定义配色，没装回退原版四档 |

把 jar 丢进 `mods/` 即可。

## 快速开始

1. 进游戏，捡起任意物品 —— 右下角出现一张卡。
2. 按 **K** 打开配置界面：左边选页，右边改值，`位置与堆叠` 页可以直接拖拽调整落点。
3. 觉得哪样东西太吵，在 `过滤` 页把它拉进黑名单；想让它永远显眼就拉进白名单。

## 主要功能

- **合并** —— 连捡同一样东西并进同一张卡（`merge.mode` 四档：同名同 NBT / 同名就并 / 改名不并 / 从不合并），合并瞬间整张卡脉冲一下、数字从旧值滚到新值。
- **过滤三表** —— 黑名单（不弹）、白名单（永远弹并强调）、静音名单（弹，但不强调）；规则支持 `minecraft:stone`、`#forge:ores`、`@somebotania` 三种写法，内置一份可整体关掉的默认忽略表（泥土圆石这类刷屏物）。
- **NEW 角标** —— 这一局头一次见到的物品亮一下；刻意不落盘，换世界就重置。
- **稀有度强调色** —— 竖条与数量同色，原版四档（白 / 黄 / 青 / 紫）；装了 RarityCore 则改用它的七档，并追加高稀有卡的入场扫光与按档阶梯的微光。
- **经验卡** —— 经验球拾取同样弹卡（下界之星图标、独立绿色），与物品卡共用合并与过滤规则。
- **只弹你的** —— 信号源头按拾取者过滤，其他玩家捡东西、僵尸捡装备都不会弹到你屏幕上。
- **位置与堆叠** —— 默认落在快捷栏右侧那条带里，右缘对齐；锚点可拖、卡片可镜像（竖条搬到最右）、间距可调、缩放可自动或手动（50%~200%）。
- **放不下的先排队** —— 同屏上限满了不顶掉旧卡，而是先排队；屏满队满之后并成一张「还有 N 项」的溢出卡。
- **动画逐项可关** —— 入场、合并脉冲、微光呼吸各自独立开关；入场与退场各有多种形态（滑出 / 拉幕、淡出 / 火车退回 / 拉幕收拢）。

## 配置

配置文件是 `config/pickupcard-client.toml`，但**建议按 K 在界面里改** —— 界面显示的是生效值，改完立刻写盘。常改的几项：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `notice.holdMs` | 4000 | 一张卡停留多久（毫秒），从最近一次刷新算起 |
| `notice.exitMs` | 480 | 退场动画时长；0 = 直接消失 |
| `merge.mode` | SAME_NBT | 合并粒度四档 |
| `layout.maxOnScreen` | 5 | 同屏最多几张 |
| `layout.queueSize` | 9 | 排队上限；0 = 不排队（屏满即丢） |
| `layout.scalePercent` | 0 | 0 = 自动（放不下才缩，下限 60%）；手动 50~200 |
| `layout.align` | RIGHT | 左缘对齐 / 右缘对齐 |
| `layout.mirrorCard` | false | 镜像卡片：竖条搬到最右缘，动画方向一起翻 |
| `layout.anchorX` / `anchorY` | -1 | 落点（屏幕比例）；-1 = 自动。**推荐在配置界面里拖** |
| `count.format` | PLUS | `+64` / `×64` / `64` / `+1.2K` |

废弃键 `stickTo` 与 `leftEdge` 读进来会被忽略（由 `anchorX` / `anchorY` 取代）。**卡面外观不在 TOML 里**，见下一节。

## 自定义卡面

卡面的配色、圆角、描边、内边距、动画时长都是数据，改外观 = 改一个文件：

1. 从 jar 里取出 `assets/pickupcard/styles/default.json`；
2. 放进资源包同路径覆盖（或直接改实例里的那份）；
3. 存盘，一秒内游戏里生效，不用重启。

模型是「主题给默认值、TOML 只覆盖你改过的项」：改了主题不会丢掉你在界面里的个人改动。设计真源与参数对照在 [docs/design.md](docs/design.md) 与 [design/](design/)（浏览器直接打开 `card.html` 看卡面语言）。

## 兼容性

| 组合 | 状态 |
| --- | --- |
| Minecraft 1.20.1 + Forge 47.4.x | ✅ 首发目标，实测在跑 |
| 纯客户端（服务器不装） | ✅ 全部功能可用 |
| 与 [UI Deck](https://github.com/E33EPUS/UIDeck) 同装 | ✅ nanovg 走 JarJar 嵌套供应，无包冲突 |
| [RarityCore](https://modrinth.com/mod/raritycore) | 🟡 可选联动（稀有度档位与配色） |
| Minecraft 1.21.1（Fabric / NeoForge / Forge） | ❌ 未实现，见下节 |
| Minecraft 1.20.1 Fabric / NeoForge | ❌ 未实现 |

## 已知限制

- **只有 1.20.1 Forge 这一个目标在产。** 1.21.1 的三个目标在 `versions/targets.json` 里显式记着 `buildable: false`：矩阵是规则，没做的那几格要在文件里看得见，而不是想不起来。
- **静音名单不压制声音。** 它只影响强调（命中者照样弹卡，但不高亮）；原版拾取音在 1.20.1 走的是不可取消的注入点，这一版没有动它。
- **窄画布上的英文标签会缩字。** 320×180 那一档（`guiScale` 拉到最大）英文芯片标签会缩得偏小 —— 不重叠，但不好读。
- **很长的过滤规则要靠悬停看全文。** 规则行只显示一行，全文在那行底部的说明条里。
- **没有服务端组件，所以拿不到只有服务端知道的信息。** 物品改名与 NBT 走的是"实体移除之前捞真身"这条路，绝大多数情况够用。

## 常见问题

**需要装到服务器上吗？** 不需要，装了也没有服务端组件。任何服务器都能用，包括原版服与别人的整合包服。

**别人捡东西我会看到卡吗？** 不会。只有你自己的拾取会弹卡。

**我捡了一整组，为什么只弹一张？** 那是合并。想每次都单开一张，把 `merge.mode` 改成 `NEVER`。

**改了 TOML 没反应？** 先确认没有旧键残留（`stickTo` / `leftEdge` / `merge.enabled` / `merge.windowMs` 都已废弃并被忽略）；改外观项要去主题 JSON，不在 TOML 里。

**卡一张都不弹？** 依次看：总开关 `enabled`、过滤页的黑名单、同屏与排队上限是否被占满。

**配置界面打不开？** 键位是 K（可在原版控制里改）。若与别的模组冲突，改用 TOML 或换键。

## 更多文档

- [docs/architecture.md](docs/architecture.md) —— 分层与装配、渲染路径、打包方式
- [docs/design.md](docs/design.md) —— 卡面设计与参数对照
- [docs/decision-rendering.md](docs/decision-rendering.md) —— 渲染方案定案与出局者
- [CHANGELOG.md](CHANGELOG.md) —— 逐版本变更（含每次就地替换的产物指纹）

## 开发与构建

本仓库是**单分支多目标**结构：`shared/` 放与平台无关的逻辑，`layers/` 放按映射 / 加载器 / 版本切分的代码，`platforms/<目标>/` 是各自的 Gradle 工程；有什么目标、各挂哪些层全部声明在 `versions/*.json`。

```bash
cd platforms/1.20.1-forge && ./gradlew build    # 编译 + 单测
python tools/verify_targets.py                   # 结构自洽性（CI 第一道闸）
```

产物在 `platforms/1.20.1-forge/build/libs/`。

## 许可证

MIT，见 [LICENSE](LICENSE)。内嵌组件（NanoVG 绑定与四平台 native、LWJGL）的许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

Copyright (c) 2026 扭曲 (E33EPUS)
