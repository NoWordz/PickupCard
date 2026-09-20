# 参照物：Loot Journal: Pickup Notifier（同赛道最成熟的那一版）

> ⚠️ **它是 ARR（All Rights Reserved，全权保留）。本文件与整个仓库都不许出现它的代码。**
> 只记"它做了什么、怎么想的"这类**事实与思路**（类名、包结构、选项名属于可查事实），
> 不抄实现：没有一行它的源码、没有复制的表达式，我们的实现一律自己写。
> 2026-09-17 用户明确要求过这条；谁要在本仓库里贴它的代码，先读这段。

用户 2026-09-17 让我们"学它的思路架构"。本文件只记录**读过源码之后确认的事实**，
以及我们从它身上拿了哪些**想法**、明确没拿什么 —— 免得下次再从 jar 反编译一遍。

- 源码：`C:\Users\NIUQU\AppData\Local\Temp\lj`（1.21.1 的 6.2.1 clone，**只读参照，不当依赖**）
- 与我们同版本的产物：`D:\Myworld\.minecraft\versions\IA\mods\loot_journal-forge-1.20.1-6.1.2.jar`
  （6.1.2 是 1.20.1 那一支；clone 是 1.21.1 —— 看架构够用，看 NBT/组件细节要留意版本差）
- 规模：81 个 Java 文件；多加载器（`common/` + `fabric/` + `neoforge/` + `buildSrc`）

## 它的骨架（三条主脉）

1. **主题是数据，不是代码**：`client/registry/*`（`ThemeRegistry`/`ResourceRegistry`/
   `ResourceKind`/`PickupResourceManager`/`CompactDispatchCodec`）→ 主题由资源包加载，
   带 codec。主题的选择靠 **匹配 DSL**：`client/themes/match/` 13 个类
   （`IsItemMatch`/`ItemTagMatch`/`ModMatch`/`RarityMatch`/`IsXPMatch`/`ItemStackMatch` +
   `AllOf/AnyOf/NoneOf/Always`）。外观由可替换部件拼出：面板
   （`FillPanel`/`NineSlicedPanel`/`NonePanel`）、横幅（`TextureBanner`/`NoneBanner`）、
   图标（`SimpleIcon`/`PickupIcon`）、图标特效（`RayGlowEffect`/`NoneEffect`）+ 变量系统
   （`ColorVariable`/`BooleanVariable`/`Var`/`VarCodec`）。
2. **排版是 token 列表**：`client/renderer/layout/tokens/*`（`IconToken`/`NameToken`/
   `CountToken`/`GapToken`/`TotalToken`，统一接口 `LayoutToken`）+ `LayoutParser`
   （解析配置里的 `ELEMENT_ORDER` 字符串）+ `PickupLayout`/`LayoutResult`/`LayoutEntry`。
   "卡里放什么、什么顺序"是**数据**。
3. **拾取是带类型的事件对象**：`PickupEvent` 把 `renderIcon(GuiGraphics, PickupRenderer)`、
   `bind(PickupStyle)`、`maybeMerge(other)`、`supportsTotalCount()`、`displayName()`、
   `count()`、`total()` 交给实现（`ItemPickupEvent`/`XpPickupEvent`/`OverflowPickupEvent`）。
   只有**一个 mixin**（`MixinClientPacketListener`）。

## 它没有的东西（我们的 HudSafeZone 是它没有的）

全仓库与"避让 HUD"有关的代码只有 `ScreenAnchor` 里两行 `getGuiScaledHeight() - offset
- entryHeight()`。**它不躲快捷栏、不躲计分板、不躲状态效果图标、不做底部安全区计算** ——
它的答案是一整套玩家旋钮：屏幕锚点（四个角）+ 生长方向 + X/Y 偏移 + 缩放
（0.1–3.0，界面按百分比显示）。**缩放是用 pose 矩阵整体缩放的**（`pose().scale(...)`），
不是逐个 token 改数值 —— 这一点我们照它的思路自己实现（见 ④）。

## 它的配置文件里有这些键（玩家可见的那一层，供对照）

开关类：总开关、物品拾取、经验拾取、溢出拾取、数字缩写、射线微光、音效（+ 音效 id）、
物品/经验追踪、按玩家过滤（+ 白名单）。
形状类：合并粒度、堆叠模式（固定槽位 / 平滑流动）、名字最大宽度、停留时长、
**同屏容量（默认 9，1..64）**、**排队上限（默认 9，0..256）**、主题名、屏幕锚点、
生长方向、X/Y 偏移、卡间距、**缩放（0.1..3.0）**、元素顺序字符串、上下左右内边距。
动画类：淡入/淡出时长、淡入/淡出缓动、脉冲强度/时长/峰值/两段缓动。
**配置界面用 YACL**（第三方配置库）+ Fabric 侧 ModMenu —— 它不自绘配置界面。

## 溢出卡（值得学的那一招）

排队也满了之后的那些拾取不再各弹一张，而是并进**一张「还有 N 项」的卡**；那张卡会按
"张数越多、轮播越慢"（间隔下限 0.2 秒）的节奏在成员图标之间轮播，让人看得出"还有别的"。
另外它会**递归数容器里的东西**（潜影盒之类）。
> 实现细则（间隔公式、取模写法）属于它的源码，本仓库不复制；我们按这个**行为**自己写。

## 我们搬了什么 / 没搬什么

- ✅ **搬**：`MergeMode` 四档谓词（我们原来的"合并窗口（毫秒）"已删，见
  `shared/src/main/java/com/niuqu/pickupcard/notice/MergeMode.java`）。
- ✅ **搬（已批准，待做）**：`DISPLAY_CAPACITY` + `QUEUE_SIZE` + 溢出卡；`SCALE`（卡片缩放，
  与"屏幕放不下就丢最老的"配合）。
- 🕓 **记为方向、本轮不做**：主题数据化（资源包 + 匹配 DSL + 可替换部件）。
- ❌ **不搬**：`ELEMENT_ORDER` 字符串（我们的 token 管线已经能表达，多个键反而是第二份真源）；
  缓动曲线做成枚举（我们的节奏是定死的，多一个旋钮就多一种"手感不对"）；
  YACL（我们选了自绘三列 + 预览，代价自己扛）。
