# Release Notes

## v0.2.5

**这一版把 0.2.4 的崩溃修干净，mod 列表从此能一键打开配置界面，卡片动画也更流畅。**

**修复**

- 修复 0.2.4 部分包体一按配置键就崩溃的问题（组件完整性由构建自动补齐并校验）
- mod 列表的「配置」按钮直接打开本 mod 的配置界面，不再落到 Configured 的通用编辑器
- 动画期间更流畅：缩放过渡不再每帧重算全部卡片文字，入场/退场的渲染提交大约减半

**更改**

- 竖条默认宽度 2→3 像素；卡片停留时长默认 4→5 秒（已保存的配置不受影响）

----

**This release fixes the 0.2.4 crash for good, makes the mod list config button open our own screen, and smooths the animations.**

**Fixed**

- Fixed the 0.2.4 crash on opening the config screen (package completeness is now assembled and verified by the build itself)
- The config button in the mod list opens this mod's own screen instead of Configured's generic editor
- Smoother animations: scale transitions no longer re-layout every card's text per frame, and render submissions during entrance/exit are roughly halved

**Changed**

- Rarity bar default width 2→3 px; card hold time default 4→5 s (saved configs are untouched)

## v0.2.4

**这一版把「捡东西」补全成「得到东西」：磁铁吸进背包的也弹卡，屏满了新卡直接顶旧卡，数字还能数背包里的总数。**

**新增**

- 磁铁检测：磁铁升级把物品吸进你的背包时也弹卡（默认开，半径可调）
- 屏满时新卡立刻顶掉最老的旧卡（想排队可在配置里切回）
- 计数口径「背包总数」：卡上的数字实时跟随原版背包（41 格）
- 五档新动画：入场「弹出回弹」「掉落」，退场「下坠」「缩放消失」，停留「摇摆」

**更改**

- 停留时长默认 3.2 秒；配置界面的时间按秒显示，全部说明文字精简成一句话

**修复**

- 配置界面悬停误亮、开关点不了、点击出蓝框、编辑场偶发崩溃，都已修复
- 高稀有度卡片的微光明显加亮

----

**Pickups are now "gains": magnets pulling into your inventory pop a card, a full screen replaces the oldest card, and the number can count your inventory.**

**Added**

- Magnet detection: magnets pulling items into your inventory pop a card (on by default, radius configurable)
- A full screen replaces the oldest card immediately (queueing can be re-enabled in the config)
- Counting mode "held total": the number follows your vanilla inventory live (41 slots)
- Five new animations: entrances bounce & drop, exits fall & shrink, and an idle sway

**Changed**

- Default hold time is 3.2s; durations show as seconds and every tooltip is one short sentence

**Fixed**

- Config hover misfires, unclickable toggles, the blue click ring and an editor crash are all fixed
- The glow on high-rarity cards is clearly louder

## v0.2.3

**换了新图标，署名与描述也一次写清楚。**

**更改**

- 新 mod 图标与项目 logo：卡片堆 + 镐子
- mod 列表的详细信息里能看到仓库链接与内嵌组件（NanoVG / LWJGL）的致谢
- 作者署名「扭曲 (E33EPUS)」，描述改为「Every item you pick up becomes a card on your HUD」

**修复**

- 静音名单现在真的静音：命中的拾取照常弹卡，但不强调、也不响原版拾取音
- README 承诺过的「内置默认忽略表」并不存在，说明已改回真实行为

----

**A new icon, with the credits and the description written down properly.**

**Changed**

- New mod icon and project logo: a stack of cards and a pickaxe
- The mod list detail view now shows the repository link and credits for the bundled components (NanoVG / LWJGL)
- Author credit is 扭曲 (E33EPUS); description is "Every item you pick up becomes a card on your HUD"

**Fixed**

- The mute list now actually mutes: matching pickups still pop a card, but are never highlighted and their vanilla pickup sound is suppressed
- README promised a built-in default ignore list that no longer exists; the wording now matches the real behaviour

## v0.2.2

**卡片贴着物品栏上缘出现，旧的向上顶；常见分辨率能同时放满 5 张。**

**新增**

- 镜像卡片：竖条搬到卡片右缘，内容反向排列，动画一并反向（默认关）
- RarityCore 联动：装了它，竖条与微光自动跟随你配的七档颜色
- 高稀有卡停稳后一道白光扫过；稀有度微光按档位更亮
- 过滤页：黑名单 / 白名单 / 静音名单，游戏内即可增删

**修复**

- 拾取第一张卡时卡顿（那一帧 42ms，现 1.6ms）
- 镜像卡片的文字与图标从反方向进出
- 退场播完整摞卡突然放大一圈；淡出最后一帧图标闪回
- 图标上下颠倒、方块发糊、附魔光效不滚动、整体比原版暗
- 稀有光圈生硬弹出，不跟卡片淡入
- 配置界面标签列没画、底部说明条顶出屏幕、滑条拖不动、主题强调色不生效
- 英文长标签穿出边框、「1 rules」单复数语法错

**更改**

- 出厂默认对齐改为右缘对齐（已保存过设置的不受影响）
- 消失方式三档（淡出 / 火车退回 / 拉幕收拢），可与入场方式自由组合
- 配置界面排版重做：固定行距、页内分小节、每页「恢复本页默认」、色块点选
- 图标改为每帧按原版现渲；退场时与卡片同步真透明淡出
- 放不下的拾取改排队等位子，不再无声消失
- 5 卡稳态绘制 ~0.9ms（本版加了预算护栏）

**升级**

- 纯客户端，服务端不用装；从 0.2.1 直接覆盖，配置文件兼容

----

**Cards now appear against the hotbar's top edge, pushing older ones up - and the common 854x480 window fits all five.**

**Added**

- Mirror card: the bar moves to the card's right edge, the row runs right to left and the animations follow (off by default)
- RarityCore integration: with it installed, the bar and glow follow the seven-tier colours you configured there
- A one-shot light sweep across high-rarity cards, and a glow that climbs per rarity tier
- Filter page: blacklist, whitelist and mute list, editable in game

**Fixed**

- Stutter on the first pickup of a session (that frame ran 42ms, now 1.6ms)
- Mirror cards: text and icons entering and exiting from the wrong side
- The stack growing a size after the exit finished, and the icon flashing back on the last fade frame
- Icons upside down, block icons blurry, the enchant glint frozen, everything dimmer than vanilla
- The rarity glow popping in instead of fading in with the card
- The config screen's label column missing, the hint line running off-screen, unresponsive sliders, theme accent colours doing nothing
- English labels overflowing, and the "1 rules" grammar slip

**Changed**

- Factory default alignment is right edge (saved settings are untouched)
- Three exit modes (fade / train-back / wipe), freely combinable with entrances
- Config screen relaid out: constant row rhythm, sections, a per-page restore, colour swatches
- Icons render through the vanilla pipeline every frame and fade out in true alpha with the card
- Pickups that do not fit queue instead of vanishing
- Five settled cards measure ~0.9ms to paint, now with budget guardrails

**Upgrading**

- Client-side only, no server install. Drop-in over 0.2.1, config compatible

## v0.2.1

**首个自绘版本：零必需依赖，纯客户端。**

**新增**

- 捡起任何东西都在 HUD 上弹一张卡，物品与经验球都算；连着捡同一样东西会并成一张，数字滚上去
- 卡片从竖条后面滑出，退场淡出；四档稀有度各有强调色
- 过滤页：黑名单 / 白名单 / 静音名单，游戏内可增删，支持物品 id、`#标签`、`@modid`
- 「同一样东西再次拾起」真的会动：整张卡脉冲 + 数字滚动
- 卡片挪到物品栏与屏幕右缘之间那条区域，并一路下到屏幕底、与物品栏同层
- 同屏上限 + 排队上限 + 溢出卡：屏满先排队，不再顶掉旧卡
- 卡片缩放：塞不进 HUD 带之上时整体等比缩，下限 60%

**更改**

- 配置界面重铸：三列、一行一项、可滚、悬停一句说明
- 卡片尺寸 32 → 20 逻辑px；入场 800ms 改 Material 曲线
- 主题数据化：几何 / 材质 / 文字 / 颜色 / 动画全部来自主题 JSON，资源包可整套覆盖
- 去掉配置项的「跟随主题」第三态：界面显示生效值，拨过哪项就写死哪项

**修复**

- 配置界面标签列整列没画
- 窄画布上底部说明与「预览已收起」相撞
- 主题里的强调色不生效
- 部分滑条拖不动
- 淡出最后一帧图标与文字完全不透明再消失
- 配置预览四档颜色全是灰的
- 淡出末段被拾起时文字与图标闪一下

**升级**

- 纯客户端，服务端不用装

----

**First self-drawn release: zero required dependencies, client-side only.**

**Added**

- Every pickup pops a card on the HUD, items and XP alike; grabbing the same thing again merges into the existing card and rolls the count
- Cards slide out from behind a bar and fade on exit, with an accent colour per rarity tier
- Filter page: blacklist, whitelist and mute list, editable in game, each taking item ids, `#tags` or `@modid`
- Picking the same item up again finally animates: the whole card pulses and the count rolls
- Cards sit between the hotbar and the right edge of the screen, dropping to the bottom so they share the hotbar's row
- A simultaneous cap, a queue cap and an overflow card: a full screen queues instead of evicting older cards
- Auto scaling: the stack scales down to fit above the HUD band, with a 60% floor

**Changed**

- Config screen rebuilt: three columns, one row per option, scrollable, one plain-language hover line each
- Card size 32 → 20 logical px; the 800ms entrance moved to the Material curve
- Theme as data: geometry, materials, text, colours and animations all come from the theme JSON, so a resource pack can restyle the whole thing
- The "follow theme" third state is gone: the screen shows the effective value and touching an option pins it

**Fixed**

- The config screen's label column was never drawn
- The bottom hint line collided with "preview collapsed" on narrow canvases
- The theme's accent colours did nothing
- Some sliders could not be dragged
- The last fade frame went fully opaque again before disappearing
- The config preview showed all four rarity colours as grey
- Text and icons flashed when a fading card was picked back up

**Upgrading**

- Client-side only, no server install
