# Release Notes

<!--
发版约定：正文 = 本文件里的版本段。
- 中文摘要在前，英文块放在【段尾】（商店 changelog 取段尾那一块）
- 段标题必须是 `## vX.Y.Z`（带 v），与 git tag 一致
-->

## v0.2.2

**卡片搬家了：新卡现在贴着物品栏上缘出现，旧的向上顶。** 常见分辨率终于放得下
同屏上限的张数 —— 854×480 窗口（最常见的 427×240 画布）实测 5 张全部原尺寸在屏，
放不下的拾取改排队等位子，不再无声消失。"动画结束图标回弹"（退场播完整摞卡突然
放大一圈）也根治了：缩放和卡宽现在有 340ms 的过渡。

配置界面这一版大整了一次排版：行距恒定、页内分小节、每页有「恢复本页默认」、
颜色改成色块点选（第一档是「跟随主题」）、预览回到"非动画页一张静止完整卡、
动画页自动演"的分工。消失方式有了三档（淡出 / 火车退回 / 拉幕收拢），可以和
入场方式自由组合；经验卡的微光会呼吸了；淡出最后一帧图标闪回的毛病修了。

**物品图标这一版彻底重做。** 上一版的图标是"离屏烘一张贴图再画"——贴图是死画面，
于是附魔光效不再滚动、图标整体发暗、方块类被钉在低分辨率上显得毛糙、方向还上下
颠倒。现在**每个图标都按原版方式逐帧现渲**：附魔光效边捡边闪、方块清晰锐利、
亮度与原版一致。退场淡出改为在淡出期间把实心渲染层换成可混合层，所以图标与卡片
同步真透明淡出，不再"变黑再消失"。

英文界面这一版补齐：所有文案（含色块标签、样例卡名）都走语言文件，长英文标签
（如 Placement & stacking）自动缩字而不是穿出边框或截断。

这一版（第 8 次就地替换）把英文界面真正收尾：每页底部说明条不再顶出屏幕边缘
（对称边距 + 超长补省略号 + 让开快捷栏 + 自带垫底），过滤页输入框占位文字缩进
框内不再出界，长名样例的数量不再贴死框缘，"1 rules" 这类单复数语法错修了；
配置界面布局给底部说明让出了专属净空带。**新增 RarityCore（稀有度核心）联动**：
装了它，卡片的竖条与微光自动改用你在 RarityCore 里配的七档颜色，没装则一切照旧
（反射接入，无需任何设置）。**新增两项稀有特效**：高稀有卡（原版稀有以上 /
RarityCore 传说以上）停稳后有一道白光扫过卡面一次；稀有度微光改为按档位爬阶梯，
档位越高越亮。性能上给渲染路径加了硬预算护栏（5 卡稳态实测绘制 ~0.9ms），并做
了三处热路径清理。全量审计见 `docs/audit-2026-09-20.md`。

这一版（第 9 次就地替换）新增**镜像卡片**：一个开关把竖条搬到卡片最右缘，往左依次
是图标、名字和数量，入场退场动画也跟着反向——适合把信息贴着屏幕右缘读的玩家。
出厂默认对齐改为**右缘对齐**（右缘齐、左缘随卡宽参差；已保存过设置的不受影响）。
稀有光圈现在会随卡片入场淡入、跟随内容移动，不再生硬地凭空出现。英文配置文案
全量重写（统一句式与术语），并修正静音名单的说明。配置界面拆出独立的行模型类，
渲染入口拆出内容路类（行为不变）。全量审计增补见 `docs/audit-2026-09-20.md`。

纯客户端，服务端不用装；不依赖 ApricityUI。从 0.2.1 直接覆盖即可，
配置文件兼容（旧的贴边/竖条位置键会被忽略，锚点在配置界面里拖）。

---

Cards now stack upward from a fixed line just above the hotbar. On the common
427x240 canvas all five cards fit at full size; pickups that do not fit queue up
instead of vanishing, and the end-of-animation scale pop is gone (340ms transitions
on scale and card width). The config screen got a layout pass: constant row rhythm,
section headers, a per-page "restore defaults" button, color swatches with
"follow theme" first, and a per-page preview (static card elsewhere, live stage on
the animation page). Exits now have three modes freely combinable with entrances,
the rarity glow breathes, and the last-frame icon flash is fixed.

Item icons were rebuilt this release. The previous build baked each icon into an
offscreen texture once - a frozen snapshot, which killed the scrolling enchant
glint, dimmed the icons, made block icons look low-resolution, and rendered them
upside down. Icons are now rerendered through the vanilla pipeline every frame:
glint animates, blocks are crisp, brightness matches vanilla. Exit fades now swap
the solid render layer for a blended one during the fade, so icons fade out in
true alpha alongside the card instead of darkening first.

The English UI is now complete: every string (including the color-swatch labels
and sample card names) comes from the language files, and long English labels
such as "Placement & stacking" scale to fit instead of overflowing or clipping.

This pass (8th in-place replacement) finishes the English UI: the per-page hint
line no longer runs off the screen edge (symmetric margins, ellipsis on overflow,
clear of the hotbar, with its own backing band), the filter text-field placeholder
scales inside its box instead of spilling out, the long-name sample's count no
longer touches the card border, and the "1 rules" grammar slip is fixed; the
config layout now reserves an exclusive strip for the hint line. **RarityCore
integration lands**: when [稀有度核心] is installed, the card bar and glow
automatically use your seven-tier colors configured in RarityCore - no setup,
reflection-based, and everything falls back to theme colors without it. **Two
rarity effects added**: high-rarity cards (rare+ vanilla / legendary+ RarityCore)
get a one-shot light sweep after settling, and the rarity glow now climbs a
per-tier intensity ladder. On the performance side the render path gained hard
budget guardrails (5 settled cards measure ~0.9 ms paint) plus three hot-path
cleanups. Full audit: see docs/audit-2026-09-20.md.

Client-side only, no hard dependencies; drop-in upgrade from 0.2.1.

This pass (9th in-place replacement) adds a **mirror card** option: one switch
moves the rarity bar to the right edge and runs the row right to left - icon,
then name and count - with entrance and exit animations mirroring too, for
reading against the screen's right side. The factory default alignment is now
**right edge** (saved settings are untouched), the rarity glow fades in with the
card's entrance and follows the content instead of popping in, and the English
config text was rewritten for consistent wording, including an honest
description of the mute list. Under the hood the config screen and the render
entry were split into smaller classes with no behavior change.

## v0.2.1

首个自绘版本：**不再需要 ApricityUI**，零必需依赖，纯客户端 —— 服务端不用装。

捡起任何东西都会在 HUD 上弹出一张卡，物品和经验球都算。同一样东西连着捡会并成一张，
数字滚上去；卡片从一条竖条后面滑出来，退场是淡出。四档稀有度各有一套强调色，
主题 JSON 可以整套换掉（几何、材质、文字、颜色、动画时长都在里面，资源包就能覆盖）。

这一版新增**过滤页**：黑名单 / 白名单 / 静音名单，每条支持物品 `minecraft:cobblestone`、
标签 `#forge:ores`、整个 mod `@modid`。这三张表此前只能手改配置文件。

**卡片挪到了物品栏右边那条区域**（物品栏与屏幕右缘之间），并一路下到屏幕底、与物品栏同层。
**同一样东西再次拾起**现在真的会动：整张卡鼓一下、数字从旧值滚上去。

**不需要任何前置模组。**

First self-drawn release: **ApricityUI is no longer required** — zero required
dependencies, client-side only, no server install needed.

Every pickup pops a card on your HUD, items and XP alike. Grabbing the same thing
again merges into the existing card with a rolling count; cards slide out from
behind a bar and fade on exit, with an accent colour per rarity tier. A theme JSON
carries the geometry, materials, text colours and animation timings, so a resource
pack can restyle the whole thing.

This version adds the **filter page**: a blacklist, a whitelist and a mute list,
each accepting item ids (`minecraft:cobblestone`), tags (`#forge:ores`) or whole
mods (`@modid`). Those three lists used to be config-file only.

Cards now sit in the region between the hotbar and the right edge of the screen,
dropping to the bottom so they share the hotbar's row. Picking the same item up
again finally animates: the whole card pulses and the count rolls from the old
value to the new one.

**No dependencies required.**

## v0.1.0

首个版本：拾取卡片提示的完整形态。

纯客户端 —— 服务端不用装。四档稀有度卡面（木牌 / 铜牌 / 蓝银 / 暗紫鎏金），
两秒内连捡同类物品合并成一张卡（数字滚动），本局首次拾取的物品带 NEW 角标。
界面由 ApricityUI 渲染，卡面与动画用 CSS 写成，改外观不用重编译。

**需要 ApricityUI 1.2.0+ 作为前置。**

First release. Client-side only, so no server install needed. Four rarity card
styles, merge-on-repeat pickups with a rolling count, and a NEW tag the first
time you ever grab an item in a session. The card look and animation are written
in CSS and rendered by ApricityUI, so restyling needs no rebuild.

**Requires ApricityUI 1.2.0+ as a dependency.**
