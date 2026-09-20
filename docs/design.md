# PickupCard 设计文档（v2 · 自绘路线）

> 本文档是 2026-09-16 逐分支拷问（grill）后的设计正本。
> **它推翻 `architecture.md` 中「渲染：为什么是 ApricityUI」一节的决策**——自绘路线胜出，
> AUI 版（当前 main 上的 v0.1.0 实现）转为归档。架构文档其余部分（单分支多目标分层、
> 代码放置判据、verify_targets 守卫）继续有效。

## 0. 谱系与现状

| 版本 | 路线 | 状态 |
| --- | --- | --- |
| D:\pickupnotice | 自绘 SDF shader + Edge 烘 CSS 位图 | 已废弃（整图拉伸、双真源、混合状态泄漏） |
| main @ v0.1.0 | ApricityUI 硬前置，HTML/CSS 卡面 | 归档（打 `archive/aui` tag），不再演进 |
| 本设计 v0.2.0 | 零依赖自绘，loot-journal 架构参考 | 待实现 |

AUI 版的**纯逻辑资产全部保留**：`shared/` 的 NoticeQueue / Notice / MergeWindow /
SeenItems / CountFormat 及其测试不依赖渲染，原样复用。要推倒的只有
`PickupBoard`（AUI DOM 同步）、`card.html/css` 和 AUI 依赖本身。

## 1. 定位

**纯拾取通知器**（对标 loot-journal）：捡到物品/经验时屏幕上弹出一张动画卡片
（图标 + 名字 + 数量），带合并、过滤、主题化。"Card" 指卡片式弹窗本身。

明确不做（v1）：图鉴/收藏册、附近玩家追踪、生物拾取、抽卡玩法、完整配置 GUI。

## 2. 参考与许可边界

- **loot-journal**（架构参考）：Obscuria Ecosystem License v1.3，**非标准开源——只学思路，禁止复制代码**。可借鉴的思想：拾取信号注入点、音效接管、数据驱动样式、布局 token 化。
- **ModernUI-MC**（渲染思想参考，LGPL-3）：借鉴动画缓动、圆角批绘制、文字渲染思路；不引入依赖。
- **RarityCore**（可选运行期联动，GPL-3）：Modrinth Maven 编译期依赖（jar 不打包其类），运行期 `ModList.isLoaded("raritycore")` 探测；**所有 API 调用收进单一 adapter 类**（`RarityCoreBridge`），隔离 GPL 接触面，删除该类即完全解除联动。

## 3. 分层落点（单分支多目标结构不变）

| 模块 | 层 | 动作 |
| --- | --- | --- |
| NoticeQueue / Notice / MergeWindow / SeenItems / CountFormat | shared | 保留（含测试） |
| **FilterRules**（三表匹配纯逻辑） | shared | 新增 |
| **StyleModel**（样式数据 + JSON 解析） | shared | 新增 |
| **CardAnimator**（easing 曲线集，纯数学可测） | shared | 新增 |
| PickupRelay（补拾取者过滤 + XP） | mapping/official | 修改 |
| ItemIdentity | mapping/official | 保留 |
| **RarityBridge**（vanilla 4 档）/ **RarityCoreBridge**（7 档+取色） | mapping/official | 新增 |
| **PickupHudRenderer**（自绘渲染器，替代 PickupBoard） | mapping/official | 新增 |
| Mixin: handleTakeItemEntity | mapping/official | 保留 |
| **Mixin: 拾取音效 WrapOperation** | mapping/official | 新增 |
| 入口 / TOML 配置 / 客户端命令 / 事件注册 | platforms/1.20.1-forge | 修改 |
| PickupBoard、card.html、card.css、AUI 依赖 | 各处 | 删除 |

## 4. 拾取管线（含拷问揪出的 bug 修复）

```
ClientPacketListenerMixin (ensureRunningOnSameThread 之后注入)
  └─ PickupRelay.onTakeItem(level, packet)
       ├─ owner = level.getEntity(packet.getPlayerId())   ← ★ 现版缺失此步（bug）
       │    owner 必须是 AbstractClientPlayer 且 UUID == 本地玩家，否则丢弃
       │    （僵尸捡装备、其他玩家拾取在源头消失——v0.1.0 会误弹）
       ├─ entity instanceof ItemEntity     → stack.copy() + amount   （改名/NBT 全保留）
       ├─ entity instanceof ExperienceOrb  → orb.getValue()           （XP 卡）
       ├─ FilterRules.check(stack)         → 黑名单丢弃 / 白名单强调 / 静音标记
       └─ PickupBoard.offer(...)           → NoticeQueue.absorb → 渲染层消费
```

- **XP 卡**：同一包白拿的数据，独立样式（绿色系，不走稀有度档位），同样受合并窗口管理。
- **音效接管**（MixinExtras `@WrapOperation` 拦 `playLocalSound(ITEM_PICKUP)`）：
  模式 = `vanilla` / `mute` / `custom`（自定义音效 id + 音量 + 音调）；静音表命中的
  物品额外压制原版"啵"声。Forge 47 自带 MixinExtras，接入前先 `build` 验证一次。

## 5. 渲染层设计（自绘）

**美术定案（2026-09-16，依据 design/card.html 实况预览）**：卡面语言 = **玻璃拟态**
（深色半透明卡 + 顶部高光细线 + 稀有度色微光；真背景模糊很贵，v1 以半透明近似，
模糊留 v2 可复用 AtomChat 金字塔模糊经验）；动画性格 = **弹性**（入场 easeOutBack
320ms 从下方 18px 回弹 + 过冲，合并数字单峰脉冲放大 1.35 倍，退场下沉 12px 缩 0.94
淡出）；密度 = **紧凑单行**（2x 图标 32px，卡高约 50px）。稀有度强调色四档：
`#9AA4AD / #FFD83D / #55EBFF / #D78BFF`，与 mockup 的 CSS 变量一致。

- **挂载点**：`RenderGuiEvent.Post`（Forge 1.20.1），`GuiGraphics` 绘制；进出场手动
  保存/还原 blend、depth 等 GL 状态（D:\pickupnotice 的混合状态泄漏教训写进实现注释）。
- **两遍绘制**：第一遍全部卡的外壳（投影/卡面/描边/高光/微光/经验珠）合一次
  `begin(TRIANGLES)` immediate 画完，零状态切换；第二遍走 GuiGraphics 批量通道画
  物品图标与文字。两遍不交错——混合状态泄漏的根治。
- **程序化圆角**：三角扇拼圆角（每角 3~8 步随半径）+ 垂直渐变逐顶点着色；StyleModel
  预留 `texture` 字段，v2 接九宫格绘制器（四角原样、中段平铺，无拉伸问题）。
- **图标**：`GuiGraphics.renderItem`——附魔光效、耐久条由原版渲染，白拿。
- **文字**：原版 `Font.drawInBatch`；数量格式走 CountFormat（`+64`/`×64`/`64`/`+1.2K`）。
- **动画**：CardAnimator = 时间驱动 + easing 库（easeOutCubic / easeOutBack /
  easeInOutQuad）；卡状态机 `ENTER → IDLE → (MERGE_BUMP) → EXIT`；入场滑入回弹、
  合并计数 pop、出场滑出淡出；**每种动画可独立开关**（配置项，动画敏感玩家友好）。
- **布局**：右下竖排、底部对齐向上生长（新卡最近视线），锚点四角可配；边距 / 卡距 /
  maxOnScreen 可配；淘汰顺序沿用 NoticeQueue 的"最久没被碰过先走"。
- **主题**：`assets/pickupcard/styles/default.json` 单一内置主题（颜色/圆角半径/描边/
  内边距/动画时长与开关/预留纹理路径），资源包同路径整体覆盖；**改主题不重编译**。

## 6. 稀有度（vanilla 兜底 + RarityCore 七档取色）

```
RarityBridge.resolve(stack) → RarityInfo{ level, accentColor }
  ├─ RarityCoreBridge（ModList 探测）：getRarity(stack) 1-7 + getRarityColor(level)
  └─ VanillaBridge（兜底）：getRarity() 0-3 → 4 档
```

- 卡面档位材质映射：`1-2 → common`、`3-4 → uncommon`、`5-6 → rare`、`7 → epic`。
- accentColor 注入 StyleModel：名字色 / 描边强调 / 稀有呼吸微光（epic 以上）。
- 稀有拾取提示音：可配音效，静音表可压制。

## 7. 过滤三表

- 匹配语法：`minecraft:stone`（物品 id）/ `#forge:ores`（tag）/ `@somebotania`（mod id）。
- 语义：**黑**=不弹卡；**白**=永远弹并强调（优先于黑）；**静音**=弹卡但无稀有音效、且压制原版拾取音。
- **默认什么都不丢**：每一次拾取都弹卡。想安静由玩家自己往黑名单里写（默认空）。
  （曾经有过一张"内置忽略表"默认挡掉泥土/圆石/沙子一类，已删除——它让玩家捡到沙子时
  完全没有任何反馈，和 mod 坏了分不出来。见 FilterRules 的类注释。）
- 双入口：TOML lists + 客户端命令（`RegisterClientCommandsEvent`：
  `/pickupcard <ignore|allow|mute> <add|remove|list> <规则>`），命令改动写回 TOML。

## 8. 配置（TOML + Schema 缝）

`config/pickupcard-client.toml`：`holdMs`、`merge.enabled/windowMs`、`layout.anchor/margin/
gap/maxOnScreen`、`count.format`、`sound.mode/id/volume/pitch`、`filter.*` 三表与默认表开关、
`animation.*` 逐动画开关、`rarity.mode = vanilla|raritycore-if-present`。

Schema 缝已存在：`PickupCardSettings` + `PickupBoard.SettingsSource`——配置 GUI（v2）
直接在这份 record 上长，不需要二次抽象。

## 9. 兼容性

- 纯客户端，任何原版/mod 服务器可用（信号来自原版广播包）。
- Sodium/Iris：HUD 事件层绘制，已知安全；上架前各跑一遍冒烟。
- **与 loot-journal 互斥提醒**（README 写明）：同信号源会双弹卡。
- 与 AUI 版互斥：装卸其一（渲染路径不同，但功能重叠无意义）。

## 10. 迁移步骤（实现顺序）

1. 打 `archive/aui` tag 归档现 main；`mod_version` 起 `0.2.0`。
2. 删 AUI 层（PickupBoard / card.html / card.css / 依赖声明），`verify_targets.py` 保绿。
3. PickupRelay 补过滤 + XP（顺手修 Mixin 注释里"拿不到谁捡的"错误说法）。
4. FilterRules（shared，先写测试）→ 三表接通。
5. CardAnimator + PickupHudRenderer（程序化圆角 + 状态机动画）。
6. StyleModel + default.json 主题；资源包覆盖验证。
7. 音效 WrapOperation + RarityBridge/RarityCoreBridge。
8. 客户端命令、README/CHANGELOG 双语重写、架构文档渲染节改指本文档。

## 11. v2 备忘（本设计明确推迟的）

九宫格纹理面板与多主题全家桶、拖拽锚点编辑（keyhud 式）、附近玩家追踪
（loot-journal 的隐私处理可直接抄思路：隐身/南瓜头不追踪、白名单）、图鉴/历史、
配置 GUI（长在 SettingsSource 上）。
