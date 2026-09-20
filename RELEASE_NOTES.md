# Release Notes

<!--
写法约定（改本文件前先读）：

1. 【一版一段】段标题必须是 `## vX.Y.Z`（带 v），与 git tag 完全一致 —— `release.yml` 拿 tag 名到
   本文件里找段，找不到就硬失败（所以：先写 notes，再打 tag）。`-rc1` 这类预发布后缀不算版本差异。
2. 【中文在前、英文在段尾，中间恰好一行 `----`】GitHub Release 正文取**整段**；
   Modrinth / CurseForge 的 changelog 取 `----` **之后**的英文块。
   【为什么用 `----` 而不是"最后一个空行"】英文块本来就会分段（`**Fixed**` + 空行 + 条目），
   按空行取只会取到最后一段、把前面几段一起丢掉；分隔线没有这个歧义。
3. 【不要拿语言名当标题】不写 "English" / "英文" 这类小标题 —— 段尾那块本来就是英文。
4. 【英文块内允许空行】切分靠 `----` 而不靠空行，这是第 2 条的推论。
5. 【面向玩家】一条一事、`- ` 列表；副标题是 `**加粗**`（不是 `###`、不是 `>`）。
   写"看得见的变化 + 要不要做什么"；根因、实现细节与产物指纹写进 `CHANGELOG.md`
   （那份是面向仓库的中文长账）。两处说法可以不同，但不能互相矛盾。
6. 【就地替换】本项目一个版本号下可能多次替换产物（铁律：不擅自升版本号）。同一段里按
   「新增 / 修复 / 更改」组织，不要写流水账 —— 逐次替换的账记在 `CHANGELOG.md`。

格式规范正本见 skill `changelog-format`。
-->

## v0.2.2

**卡片搬家了：新卡贴着物品栏上缘出现，旧的向上顶。**

**位置与容量**

- 854×480 窗口（最常见的 427×240 画布）实测 5 张全部原尺寸在屏
- 放不下的拾取改排队等位子，不再无声消失
- "动画结束图标回弹"（退场播完整摞卡突然放大一圈）根治：缩放和卡宽有 340ms 的过渡

**物品图标（本版彻底重做）**

- 上一版的图标是"离屏烘一张贴图再画"——贴图是死画面：附魔光效不再滚动、图标整体发暗、方块类被钉在低分辨率上显得毛糙、方向还上下颠倒
- 现在**每个图标都按原版方式逐帧现渲**：附魔光效边捡边闪、方块清晰锐利、亮度与原版一致
- 退场淡出改为在淡出期间把实心渲染层换成可混合层：图标与卡片同步真透明淡出，不再"变黑再消失"

**配置界面**

- 排版大整一次：行距恒定、页内分小节、每页有「恢复本页默认」
- 颜色改成色块点选（第一档是「跟随主题」）
- 预览回到"非动画页一张静止完整卡、动画页自动演"的分工
- 消失方式有了三档（淡出 / 火车退回 / 拉幕收拢），可以和入场方式自由组合
- 经验卡的微光会呼吸；淡出最后一帧图标闪回的毛病修了
- 每页底部说明条不再顶出屏幕边缘；过滤页输入框占位文字不再出界；长名样例的数量不再贴死框缘；「1 rules」这类单复数语法错修了
- **镜像卡片**：一个开关把竖条搬到卡片最右缘，往左依次是图标、名字和数量，入场退场动画也跟着反向
- 出厂默认对齐改为**右缘对齐**（已保存过设置的不受影响）；稀有光圈随卡片入场淡入、跟随内容移动
- **英文界面补齐并重写**：所有文案（含色块标签、样例卡名）都走语言文件，长英文标签（如 Placement & stacking）自动缩字而不是穿出边框或截断

**新增**

- **RarityCore（稀有度核心）联动**：装了它，卡片的竖条与微光自动改用你在 RarityCore 里配的七档颜色；没装则一切照旧（反射接入，无需任何设置）
- **两项稀有特效**：高稀有卡（原版稀有以上 / RarityCore 传说以上）停稳后有一道白光扫过卡面一次；稀有度微光改为按档位爬阶梯，档位越高越亮
- 性能遥测：单帧峰值、慢帧探针（>4ms 记一条带上下文的日志，正式版也生效）、拾取记账探针

**修复**

- **镜像卡片的动画方向**：外壳与内容两路各推了一遍镜像坐标，文字与图标在入场时从反方向冒出来（稳态看不出、动画途中才现形）。现在抽成一份共享几何（`BodyGeometry`），两路同吃一份、位移只加不乘方向，并补了单测钉住
- **「捡起来好卡」**：分层计时实测第一张卡那一帧要 42ms（外壳首次绘制 23ms + 图标首渲 27ms + 字体量宽 10ms）。现在这些账在进世界后分 7 帧摊付，第一次拾取那一帧只剩 1.6ms、一轮卡堆峰值 53ms 降到 7.8ms

**性能**

- 渲染路径加了硬预算护栏（5 卡稳态实测绘制 ~0.9ms），并做了三处热路径清理

**升级**

- 纯客户端，服务端不用装；不依赖 ApricityUI。从 0.2.1 直接覆盖即可，配置文件兼容
  （旧的贴边/竖条位置键会被忽略，锚点在配置界面里拖）
- 全量审计见 `docs/audit-2026-09-20.md`

----

**Cards moved: new cards now appear against the hotbar's top edge, pushing older ones up.**

**Placement and capacity**

- On the common 427x240 canvas (an 854x480 window) all five cards fit at full size
- Pickups that do not fit now queue for a slot instead of vanishing silently
- The end-of-animation icon rebound (the stack suddenly growing a size once the exit finishes) is gone: scale and card width run through a 340ms transition

**Item icons (rebuilt this release)**

- The previous build baked each icon into an offscreen texture once — a frozen snapshot, which killed the scrolling enchant glint, dimmed the icons, made block icons look low-resolution, and rendered them upside down
- Icons are now rerendered through the vanilla pipeline every frame: the glint animates as you pick things up, blocks are crisp, brightness matches vanilla
- Exit fades now swap the solid render layer for a blended one during the fade, so icons fade out in true alpha alongside the card instead of darkening first

**Config screen**

- A layout pass: constant row rhythm, section headers, a per-page "restore defaults"
- Colour swatches with "follow theme" first
- Per-page preview: a static complete card on the non-animation pages, the live stage on the animation page
- Three exit modes (fade / train-back / wipe), freely combinable with entrances
- The XP card's glow breathes, and the last-frame icon flash on fade-out is fixed
- The per-page hint line no longer runs off the screen edge, the filter placeholder no longer spills out of its box, the long-name sample's count no longer touches the border, and the "1 rules" grammar slip is fixed
- **Mirror card**: one switch moves the bar to the card's right edge and runs the row right to left — icon, then name and count — with entrance and exit animations mirrored too
- Factory default alignment is now **right edge** (saved settings are untouched); the rarity halo fades in with the entrance and follows the content
- **The English UI is complete and rewritten**: every string (including swatch labels and sample card names) comes from the language files, and long English labels such as "Placement & stacking" scale to fit instead of overflowing or clipping

**Added**

- **RarityCore integration**: when it is installed the card bar and glow automatically use your seven-tier RarityCore colours, no setup required (reflection-based); without it everything falls back to theme colours
- **Two rarity effects**: high-rarity cards (rare+ vanilla, legendary+ RarityCore) get a one-shot light sweep after settling, and the rarity glow climbs a per-tier intensity ladder
- Performance telemetry: per-frame peak, a slow-frame probe (one context-carrying log line above 4ms, in release builds too) and a pickup accounting probe

**Fixed**

- **Mirror card animation direction**: the shell and the content path each pushed the mirror coordinate, so text and icons appeared from the opposite side during the entrance (invisible at rest, only visible mid-animation). Both paths now share one geometry (`BodyGeometry`), displacement only adds, and unit tests pin it
- **"Stutter when picking something up"**: layered timing measured 42ms on the first card's frame (shell first draw 23ms, icon first render 27ms, text measurement 10ms). Those costs are now amortised over 7 frames after joining a world, leaving 1.6ms on the first pickup and dropping the stack peak from 53ms to 7.8ms

**Performance**

- Hard budget guardrails on the render path (5 settled cards measure ~0.9ms paint) plus three hot-path cleanups

**Upgrading**

- Client-side only, no server install, no ApricityUI dependency. Drop-in over 0.2.1; the config file stays compatible (the old edge/bar position keys are ignored — drag the anchor in the config screen)
- Full audit: `docs/audit-2026-09-20.md`

## v0.2.1

**首个自绘版本：不再需要 ApricityUI，零必需依赖，纯客户端 —— 服务端不用装。**

**新增**

- 捡起任何东西都会在 HUD 上弹出一张卡，物品和经验球都算；同一样东西连着捡会并成一张，数字滚上去
- 卡片从一条竖条后面滑出来，退场是淡出；四档稀有度各有一套强调色
- **过滤页**：黑名单 / 白名单 / 静音名单，每条支持物品 `minecraft:cobblestone`、标签 `#forge:ores`、整个 mod `@modid`。这三张表此前只能手改配置文件
- 「同一样东西再次拾起」现在真的会动：整张卡鼓一下、数字从旧值滚上去
- 卡片挪到了物品栏右边那条区域（物品栏与屏幕右缘之间），并一路下到屏幕底、与物品栏同层
- 合并粒度四档、同屏上限 + 排队上限 + 溢出卡（屏满先排队，不再顶掉旧卡）
- 卡片缩放：自动档按"一摞卡塞不塞得进 HUD 带之上"整体等比缩，下限 60%
- 无人值守跑测 harness：自己进世界 → 注入样例 → 操作 → 截图 → 自己退出

**更改**

- 渲染收口成 NanoVG 矢量一条路；SDF 形状层与整卡回退**全部删除** —— 引擎起不来就整帧不画并留一条 ERROR，不留"只在别人机器上跑得起来"的第二实现
- 配置界面重铸：三列（标签列 / 配置列 / 预览列）、一行一项、可滚、悬停一句人话；控件也是自绘的
- 卡片尺寸 32 → 20 逻辑px；底部留白不再是魔数，按原版 HUD 矩形算出来
- 入场 800ms，曲线取 Material 标准曲线
- **主题数据化**：几何 / 材质 / 文字 / 强调色 / 动画全部来自主题 JSON，资源包可整套覆盖
- 配置项没有「跟随主题」第三态了：界面显示生效值，拨过哪项就写死哪项

**修复**

- 配置界面**标签列整列没画**（控件在、也能点、单测全绿，就是没画）；顺带让这一类漏画自报
- 窄画布上底部的悬停说明与「预览已收起（窗口太窄）」两段字相撞（320×240 就已贴死，256 宽时重叠 67 逻辑px）
- **主题里的强调色是死数据**：两份主题 JSON 从第一天就写着 `accent.*`，但没有任何代码读它，改主题"改了没反应"
- 部分滑条拖不动（值供给器读的是打开页面那一刻的快照）
- 淡出最后一帧图标与文字完全不透明再消失 → 改成"淡回"
- harness 从来没能自己进世界、HUD 模式拍完不退出

**说明**

- 0.1.0 是 ApricityUI 硬前置的版本（HTML/CSS 卡面），已打 `archive/aui` tag 归档。本版是**另起一条线**：零依赖自绘，按 `docs/design.md` 的定案重做，不向下兼容 0.1.0 的配置

----

**First self-drawn release: ApricityUI is no longer required — zero required dependencies, client-side only, no server install needed.**

**Added**

- Every pickup pops a card on your HUD, items and XP alike; grabbing the same thing again merges into the existing card with a rolling count
- Cards slide out from behind a bar and fade on exit, with an accent colour per rarity tier
- **Filter page**: a blacklist, a whitelist and a mute list, each accepting item ids (`minecraft:cobblestone`), tags (`#forge:ores`) or whole mods (`@modid`). Those three lists used to be config-file only
- Picking the same item up again finally animates: the whole card pulses and the count rolls from the old value to the new one
- Cards now sit in the region between the hotbar and the right edge of the screen, dropping to the bottom so they share the hotbar's row
- Four merge granularities, a simultaneous cap, a queue cap and an overflow card (a full screen queues instead of evicting older cards)
- Auto card scaling: the whole stack scales down to fit above the HUD band, with a 60% floor
- An unattended test harness: join a world, inject samples, act, screenshot and quit by itself

**Changed**

- Rendering collapsed onto one NanoVG vector path; the SDF shape layer and the full-card fallback were **deleted** — if the engine cannot start, the frame is not drawn at all and an ERROR is logged, rather than keeping a second implementation that only works on someone else's machine
- Config screen rebuilt: three columns (labels / config / preview), one row per option, scrollable, one plain-language hover line each; the controls are self-drawn too
- Card size 32 → 20 logical px; the bottom padding is computed from the vanilla HUD rectangle instead of a magic number
- Entrance runs 800ms on the Material standard curve
- **Theme as data**: geometry, materials, text, accent colours and animations all come from the theme JSON, so a resource pack can restyle the whole thing
- The config options no longer have a third "follow theme" state: the screen shows the effective value, and touching an option pins it

**Fixed**

- The config screen's **label column was never drawn** (the controls were there, clickable, and the unit tests were green — it simply was not painted); that class of missing draw now reports itself
- On narrow canvases the bottom hover line and the "preview collapsed (window too narrow)" text collided (already touching at 320x240, overlapping by 67 logical px at 256)
- **The theme's accent colours were dead data**: both theme JSONs had carried `accent.*` since day one but no code read it, so editing the theme "did nothing"
- Some sliders could not be dragged (the value supplier read a snapshot taken when the page opened)
- The last fade frame went fully opaque again before disappearing → changed to a fade-back
- The harness never managed to join a world by itself, and HUD mode never exited after capturing

**Notes**

- 0.1.0 required ApricityUI (HTML/CSS card faces) and is archived under the `archive/aui` tag. This release starts a new line: zero-dependency, self-drawn, rebuilt to `docs/design.md`, and not config-compatible with 0.1.0

## v0.1.0

**首个版本：拾取卡片提示的完整形态。**

**新增**

- 纯客户端嗅探原版拾取包（Mixin `ClientPacketListener#handleTakeItemEntity`），服务端无需安装。注入点选在 `ensureRunningOnSameThread` 之后 —— 此时物品实体尚未移除，能拿到含改名与 NBT 的真实 `ItemStack`
- 四档稀有度卡面（木牌 / 铜牌 / 蓝银 / 暗紫鎏金），装饰层数递增
- 合并：窗口内连续拾取同类物品累加数量并触发数字跳动，而不是重复弹出
- NEW 角标：本局首次遇到的物品亮一下。只记内存、不落盘
- 附魔光效与耐久条由原版物品渲染承担
- 配置：停留时长、合并开关与窗口、同时在屏上限、数量写法
- 结构守卫 `tools/verify_targets.py`：矩阵规则 / 身份唯一 / 层谓词与挂载一致 / 工程与条目互存

**说明**

- 界面由 ApricityUI 渲染，卡面与动画用 CSS 写成，改外观不用重编译
- **需要 ApricityUI 1.2.0+ 作为前置**
- 仓库重建于 2026-09-16。更早的 `D:\pickupnotice` 是自己写 SDF 着色器 + 烘焙位图贴图的路线，
  因"烘焙贴图被三段拉伸导致细节糊、且设计稿与实现漂移"而整体重做；老仓库以
  `archive/terminal-card-wip` 标签留档，不再维护

----

**First release: the complete form of the pickup card notification.**

**Added**

- Client-side sniffing of the vanilla pickup packet (Mixin `ClientPacketListener#handleTakeItemEntity`); no server install needed. The injection point sits after `ensureRunningOnSameThread`, while the item entity still exists, so the real `ItemStack` (custom name and NBT included) is available
- Four rarity card styles (wood / copper / blue-silver / dark purple gilt) with increasing decoration
- Merging: consecutive pickups of the same item inside a window add to the count and animate the number instead of popping a second card
- A NEW tag the first time you ever grab an item in a session. In memory only, never written to disk
- The enchant glint and durability bar come from vanilla item rendering
- Config: dwell time, merge toggle and window, simultaneous cap, count format
- Structural guard `tools/verify_targets.py`: matrix rules, identity uniqueness, layer predicates matching their mounts, and projects/entries existing in pairs

**Notes**

- The interface is rendered by ApricityUI; card faces and animations are written in CSS, so restyling needs no rebuild
- **Requires ApricityUI 1.2.0+ as a dependency**
- The repository was rebuilt on 2026-09-16. The earlier `D:\pickupnotice` wrote its own SDF shaders and baked bitmap textures; because the baked textures were stretched in three slices (blurring detail) and the design drifted from the implementation, it was redone from scratch. The old repository is kept under the `archive/terminal-card-wip` tag and is no longer maintained
