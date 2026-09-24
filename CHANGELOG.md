# Changelog

## v0.2.3

**新增**

- 新的 mod 图标与项目 logo（卡片堆 + 镐子）
- mod 列表的详细信息里补上仓库链接与内嵌组件致谢（NanoVG / LWJGL）

**更改**

- 作者署名改为「扭曲 (E33EPUS)」
- mod 描述改为一句「Every item you pick up becomes a card on your HUD」
- 仓库地址集中到 `gradle.properties` 的 `mod_repo_url`，资源与文档里不再各写一份
- README 顶部放 logo，页脚补作者 / 仓库 / 问题反馈

**修复**

- README 里「CHANGELOG 含产物指纹」这句早已过期
- 结构闸与产物闸补上身份校验：占位值、许可与 `LICENSE` 对不上、仓库地址硬编码进资源，现在都会红
- README 承诺过的「内置默认忽略表」并不存在（只剩三张玩家自填的表），说明已改回真实行为
- 静音名单现在真的静音：命中的拾取照常弹卡，但不响原版拾取音（此前判定算出来了却没接到放音侧）

----

**Added**

- A new mod icon and project logo (a stack of cards and a pickaxe)
- The mod list detail view now carries the repository link and a credit line for the bundled components (NanoVG / LWJGL)

**Changed**

- Author credit is now 扭曲 (E33EPUS)
- Description is now the one-liner "Every item you pick up becomes a card on your HUD"
- The repository URL lives in `gradle.properties` as `mod_repo_url` only; resources and docs no longer keep copies of their own
- README gained the logo on top and author / repository / issues links at the bottom

**Fixed**

- README claimed the changelog carried artifact fingerprints; it has not for a while
- The structural and artifact gates now check identity: placeholder values, a licence that disagrees with `LICENSE`, and a hardcoded repository URL all fail the build
- README promised a built-in default ignore list that no longer exists (only the three player-written lists remain); the wording now matches the real behaviour
- The mute list now actually mutes: matching pickups still pop a card, but the vanilla pickup sound is suppressed (the decision was computed but never wired to the sound side)

## v0.2.2

**修复**

- 镜像卡片入场/退场时文字与图标从反方向冒出
- 拾取第一张卡时卡顿（那一帧 42ms，现 1.6ms）
- 退场播完整摞卡突然放大一圈
- 淡出最后一帧图标闪回全亮
- 图标上下颠倒、方块类发糊、附魔光效不滚动、整体比原版暗
- 稀有光圈生硬弹出，不跟卡片淡入、不跟内容移动
- 切换「右缘对齐」时卡列瞬移
- 配置界面标签列整列没画
- 窄画布上底部悬停说明与「预览已收起」两段文字重叠
- 主题里的强调色不生效
- 部分滑条拖不动
- 英文长标签穿出边框、行标签被截尾、过滤页占位文字出界、长名样例的数量贴死框缘
- 「1 rules」单复数语法错
- 静音名单的说明改成真实行为（弹卡但不强调）

**新增**

- 镜像卡片：竖条搬到卡片最右缘，内容反向排列，入场退场动画一并反向（`layout.mirrorCard`，默认关）
- RarityCore 联动：装了它，竖条与微光自动改用你在它里面配的七档颜色
- 高稀有卡停稳后一道白光扫过卡面（一次性）
- 稀有度微光按档位爬强度
- 过滤页：黑名单 / 白名单 / 静音名单，支持物品 id、`#标签`、`@modid`
- 性能遥测：单帧峰值、慢帧探针（>4ms 记一条日志，正式版也生效）

**UI 优化**

- 卡片改为贴着物品栏上缘出现、旧的向上顶；常见分辨率（854×480 窗口 / 427×240 画布）能同时放满 5 张原尺寸的卡
- 放不下的拾取改排队等位子，不再无声消失
- 缩放与卡宽加 340ms 过渡
- 消失方式三档（淡出 / 火车退回 / 拉幕收拢），可与入场方式自由组合
- 经验卡微光呼吸
- 图标改为按原版方式逐帧现渲：附魔光效滚动、方块清晰、亮度与原版一致
- 退场淡出时图标与卡片同步真透明，不再「变黑再消失」
- 配置界面：行距恒定、页内分小节、每页「恢复本页默认」、颜色改色块点选（第一档「跟随主题」）
- 预览分工：非动画页一张静止完整卡，动画页自动演
- 出厂默认对齐改为右缘对齐（已保存过设置的不受影响）
- 英文界面补齐：全部文案走语言文件，长标签自动缩字

**性能**

- 进世界后分 7 帧预热：第一张卡那帧 42ms → 1.6ms，一轮卡堆峰值 53ms → 7.8ms
- 5 卡稳态绘制 ~0.9ms；加了硬预算护栏（超线报 ERROR）

**配置**

- 新增 `layout.mirrorCard`（默认关）
- 入场与消失方式各三档
- 出厂默认对齐由左缘改右缘
- 主题新增 `material.shimmerAlpha`（0 = 关，默认 90）

**升级**

- 纯客户端，服务端不用装；从 0.2.1 直接覆盖，配置文件兼容
  （旧的贴边/竖条位置键会被忽略，锚点在配置界面里拖）

----

**Fixed**

- Mirror cards: text and icons entered and exited from the wrong side
- Stutter on the first pickup of a session (that frame ran 42ms, now 1.6ms)
- The stack growing a size once the exit animation finished
- The icon flashing back to full brightness on the last frame of a fade
- Icons upside down, block icons blurry, the enchant glint frozen, everything dimmer than vanilla
- The rarity glow popping in instead of fading in with the card and following the content
- The card column teleporting when switching to right-edge alignment
- The config screen's label column never being drawn
- The bottom hint line colliding with "preview collapsed" on narrow canvases
- Theme accent colours doing nothing
- Some sliders not responding to dragging
- English labels overflowing: long tab labels, truncated row labels, the filter placeholder, the long-name sample's count
- The "1 rules" grammar slip
- The mute list promising to silence sounds it never silenced

**Added**

- Mirror card: the bar moves to the card's right edge, the row runs right to left and the animations follow (`layout.mirrorCard`, off by default)
- RarityCore integration: with it installed, the bar and glow use the seven-tier colours you configured there
- A one-shot light sweep across high-rarity cards once they settle
- Rarity glow climbing a per-tier intensity ladder
- Filter page: blacklist, whitelist and mute list, each taking item ids, `#tags` or `@modid`
- Perf telemetry: per-frame peak and a slow-frame probe (one log line above 4ms, in release builds too)

**UI**

- Cards now appear against the hotbar's top edge and push older ones up; on the common 854x480 window (a 427x240 canvas) all five cards fit at full size
- Pickups that do not fit queue instead of vanishing
- Scale and card width run through a 340ms transition
- Three exit modes (fade / train-back / wipe), freely combinable with entrances
- The XP card's glow breathes
- Icons render through the vanilla pipeline every frame: glint animates, blocks are crisp, brightness matches vanilla
- Icons fade out in true alpha with the card instead of darkening first
- Config screen: constant row rhythm, section headers, a per-page "restore defaults", colour swatches with "follow theme" first
- Per-page preview: a static complete card on the non-animation pages, the live stage on the animation page
- Factory default alignment is now right edge (saved settings are untouched)
- The English UI is complete: every string comes from the language files and long labels scale to fit

**Performance**

- Costs are amortised over 7 frames after joining a world: the first card's frame went from 42ms to 1.6ms and the stack peak from 53ms to 7.8ms
- Five settled cards measure ~0.9ms to paint, with hard budget guardrails that log an error when exceeded

**Config**

- New key `layout.mirrorCard` (off by default)
- Three entrance modes and three exit modes
- Factory default alignment changed from left to right edge
- New theme key `material.shimmerAlpha` (0 disables, default 90)

**Upgrading**

- Client-side only, no server install. Drop-in over 0.2.1, config compatible (the old edge/bar position keys are ignored - drag the anchor in the config screen)

## v0.2.1

**新增**

- 捡起任何东西都在 HUD 上弹一张卡，物品与经验球都算；同一样东西连着捡会并成一张并滚动数字
- 卡片从竖条后面滑出，退场淡出；四档稀有度各有强调色
- 过滤页：黑名单 / 白名单 / 静音名单，游戏内可增删，支持 `minecraft:cobblestone` / `#forge:ores` / `@modid`
- 「同一样东西再次拾起」真的会动：整张卡脉冲 + 数字从旧值滚上去
- 卡片挪到物品栏与屏幕右缘之间那条区域，并一路下到屏幕底、与物品栏同层
- 合并粒度四档、同屏上限 + 排队上限 + 溢出卡（屏满先排队，不顶掉旧卡）
- 卡片缩放：按「一摞卡塞不塞得进 HUD 带之上」整体等比缩，下限 60%
- 无人值守跑测 harness：自己进世界 → 注入样例 → 操作 → 截图 → 自己退出

**更改**

- 渲染收口成 NanoVG 矢量一条路；删掉 SDF 形状层与整卡回退（引擎起不来就整帧不画并记 ERROR，不留第二实现）
- 配置界面重铸：三列（标签 / 配置 / 预览）、一行一项、可滚、悬停一句说明；控件自绘
- 卡片尺寸 32 → 20 逻辑px；底部留白按原版 HUD 矩形算
- 入场 800ms，曲线改 Material 标准曲线
- 主题数据化：几何 / 材质 / 文字 / 强调色 / 动画全部来自主题 JSON，资源包可整套覆盖
- 去掉配置项的「跟随主题」第三态：界面显示生效值，拨过哪项就写死哪项

**修复**

- 配置界面标签列整列没画（控件在、能点、单测全绿，就是没画；现在漏画会自报）
- 窄画布上底部悬停说明与「预览已收起（窗口太窄）」相撞
- 主题里的强调色是死数据（写了但没代码读，改主题没反应）
- 部分滑条拖不动
- 淡出最后一帧图标与文字完全不透明再消失 → 改为「淡回」
- harness 从来没能自己进世界、HUD 模式拍完不退出
- 配置预览四档颜色全是灰的（改带真实稀有度，开屏时用 registry 核对）
- 淡出末段被拾起时文字与图标闪一下（淡回 160ms → 300ms 且可调；淡到很淡的不再救回，改播入场）

**说明**

- 本版是另起一条线：零依赖自绘，按 `docs/design.md` 的定案重做

----

**Added**

- Every pickup pops a card on the HUD, items and XP alike; grabbing the same thing again merges into the existing card and rolls the count
- Cards slide out from behind a bar and fade on exit, with an accent colour per rarity tier
- Filter page: blacklist, whitelist and mute list, editable in game, each taking `minecraft:cobblestone` / `#forge:ores` / `@modid`
- Picking the same item up again finally animates: the whole card pulses and the count rolls from the old value
- Cards sit in the region between the hotbar and the right edge of the screen, dropping to the bottom so they share the hotbar's row
- Four merge granularities, a simultaneous cap, a queue cap and an overflow card (a full screen queues instead of evicting older cards)
- Auto scaling: the whole stack scales down to fit above the HUD band, with a 60% floor
- An unattended test harness: join a world, inject samples, act, screenshot and quit by itself

**Changed**

- Rendering collapsed onto one NanoVG vector path; the SDF shape layer and the full-card fallback are gone (if the engine cannot start, nothing is drawn and an ERROR is logged - no second implementation)
- Config screen rebuilt: three columns (labels / config / preview), one row per option, scrollable, one plain-language hover line each; the controls are self-drawn
- Card size 32 → 20 logical px; bottom padding computed from the vanilla HUD rectangle
- Entrance runs 800ms on the Material standard curve
- Theme as data: geometry, materials, text, accent colours and animations all come from the theme JSON, so a resource pack can restyle the whole thing
- The "follow theme" third state is gone: the screen shows the effective value and touching an option pins it

**Fixed**

- The config screen's label column was never drawn (the controls were there, clickable, and the tests were green - it simply was not painted; missing draws now report themselves)
- The bottom hover line collided with "preview collapsed (window too narrow)" on narrow canvases
- The theme's accent colours were dead data (written but read by no code)
- Some sliders could not be dragged
- The last fade frame went fully opaque again before disappearing → now a fade-back
- The harness never managed to join a world by itself, and HUD mode never exited after capturing
- The config preview showed all four rarity colours as grey (now each carries its real rarity, checked against the registry at startup)
- Text and icons flashed when a fading card was picked back up (fade-back 160ms → 300ms and configurable; cards already nearly invisible replay the entrance instead)

**Notes**

- This release starts a new line: zero-dependency, self-drawn, rebuilt to `docs/design.md`
