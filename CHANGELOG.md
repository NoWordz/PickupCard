# Changelog

<!--
写法约定（改本文件前先读）：

1. 【一版一段】段标题必须是 `## vX.Y.Z`（带 v），与 git tag 一致。一个版本号只出现一次 ——
   同版本多次交付在段内写 `**修复（2.3.7 补发 9）**` 这类副标题，不要另起一个 `## vX`
   （重复段标题会让"取哪一段"变成掷骰子）。
2. 【副标题是加粗，不是 `###`】`###` 与 `>` 都不做副标题。副标题可以是分类名
   （新增 / 修复 / 更改 / 性能 / 配置 / 说明），也可以是一句主题（`**修复：图标改为每帧现渲**`）。
   `>` 只用于段标题正下方的版本级说明。
3. 【正文一律 `- ` 列表】一条一事。只有「说明」类副标题下允许短段落。
4. 【中文在前、英文在段尾，中间恰好一行 `----`】切分靠分隔线、不靠空行（英文块内部允许空行）。
   —— 按空行切是曾经的真实 bug：英文块自己分段后只会取到最后一段。本节若只有中文，不写分隔线。
5. 【不要拿语言名当标题】不写 `### English` / `### 英文` —— 段尾那块本来就是英文。
6. 【本文件是仓库长账】写根因、实现、产物指纹、测试数；面向玩家的浓缩摘要写在
   `RELEASE_NOTES.md`。两处说法可以不同，但不能互相矛盾。
7. 【机器契约】各平台 `build.gradle` 的 `changelogFor()` 按 `----` 分隔线切英文块 ——
   改版式必须同步改它（旧写法 `lastIndexOf('\n\n')` 会静默丢掉前面的英文小节）。

格式规范正本见 skill `changelog-format`。
-->

## v0.2.2

> **注意：这一段的 jar 被就地替换过十次（2026-09-19 深夜起）。** 用户要求不改版本号：
> 第一次五处验收修复，第二次配置界面架构重构，第三次与 UI Deck 同装的 JPMS 打包修复，
> 第四次淡出时序与预览缩影，第五次图标离屏贴图根治，第六次图标几何修复 + 全量语言
> key，第七次图标治本（删离屏贴图、改每帧现渲 + 退场换渲染层），第八次英文界面收尾
> + RarityCore 联动 + 稀有特效 + 性能护栏，第九次镜像卡片 + 右缘默认 + 光圈动画 +
> 拆类 + 英文文案重写（见下）。旧产物
> `sha256 c9952758…` → `9ef6a724…` → `6d196671…` → `518896c9…` → `b6f40c1c…`
> → `2926b058…` → `636d2b9f…` → `224c5c06…` → `dd52d0ef…` → `0012970b…` → `0fe7bbca…`，现行产物以
> `pickupcard-Forge-1.20.1-0.2.2.jar` 当时的 sha256 为准（本次 `0fe7bbca…`）。判据：
> 包里有 `META-INF/jarjar/` = 打包修复后；有 `FadingItemBuffers` 类、无 `ItemIconCache`
> 类 = 第七次后；lang 目录含 150+ 键的 en_us/zh_cn = 第六次后；有
> `compat/RarityCoreBridge` 类与 lang 内 `filter.count.one` 键 = 第八次后；
> 有 `NvgCardContent`/`ConfigRows` 类与 lang 内 `row.mirror.name` 键 = 第九次后；

**镜像入场方向修复 + 首帧卡顿根治（09-20，第十次就地替换）**

- **镜像动画方向修复（用户报"卡片动画镜像了、文字动画没有"）**：真 bug —— 外壳与内容
两路各推了一遍镜像坐标，内容路把滑出方向乘反了：入场/退场时外壳从竖条（右）那侧滑出、
图标与文字却从左边冒出来（稳态截图完全看不出，只有动画途中现形）。根治 = 抽出
`BodyGeometry`（卡面三框自然位置的唯一出处，常规/镜像各一份），外壳与内容路同吃一份，
位移一律 `+shift` 不再乘方向（方向因子只在 `bodyShiftOf` 里）。+4 例单测（含
"镜像即翻转"与退化卡宽）。
- **首帧卡顿根治（用户报"拾起的时候好卡"）**：分层计时实测首帧 42ms —— 外壳首绘 23ms
（NanoVG 驱动"第一次真正画东西"才编着色器，此前只建上下文不画，钱白付）+ 图标 27ms
（每个物品第一次现渲都要烘焙模型）+ 字体 10ms（`font.width` 第一次量宽触发字形按需
栅格化）。根治 = **分帧预热**（进世界后 7 帧摊付：外壳两轮几何含微光/扫光/镜像分支 →
字体 ASCII 量宽 → 图标按原版渲染层分类每帧 2 个，含真卡用到的信标/龙蛋/附魔书）。
实测首帧 42ms→1.6ms、轮内峰值 53ms→7.8ms（<半帧线）。预热有效性有硬证据：外壳首轮
23ms → 次轮 0.1ms。RC 联动桥解析同移到进世界时（`RarityCoreBridge.warmUp`）。
- **性能遥测（把"卡不卡"变成可读的数）**：`BatchStats` 计每帧原版批次提交次数
（入场期每卡 3 次、一摞 5 卡 16 次 —— 这是入场帧开销的主要变量）；`CardStage.Stats`
增加单帧峰值（本轮卡堆最慢的一帧 + 形态）、`[慢帧]` 探针（>4ms 记一条带
进场/退场/flushes 上下文的日志，上限 12 条不刷屏，正式版也生效）；`[拾取]` 探针
（单次记账 >4ms 告警）。harness：入场逐帧 trace 扩到 22 tick + 入场中途两张截图
（rise≈0.3/0.7）+ 峰值护栏（>8ms 报 ERROR）+ `-PpcProfile=1` 分段计时
（flush/begin/逐卡/end/图标/文字）。
- **性能预算与既有护栏**：稳态 5 卡 paint 624us、layout 49us（基线 485/32 量级不变）；
入场期 1.0-2.2ms/帧。
- **验收**：153 单测全绿；镜像入场中途两张截图 + 稳态 PIL 像素级验证；2026-09-20
真机验收通过（首捡不卡、镜像入场方向正确）。

**镜像卡片 + 右缘对齐默认 + 光圈动画 + 拆类 + 英文文案重写（09-20，第九次就地替换）**

- **镜像卡片（新增开关 `layout.mirrorCard`，默认关）**：竖条移到卡片最右缘，往左
依次是图标格、名字、数量；入场"从竖条后面滑出"、退场火车/拉幕的方向全部跟着镜像，
数量改左锚、名字右对齐、扫光从右往左。全部走真坐标反算——**不用 `scale(-1,1)`
变换糊弄**（那会镜像文字、翻面 3D 图标）。`RevealWindow` 增加镜像分支（+三例单测），
预览舞台与真卡同公式自动镜像。
- **右缘对齐改为出厂默认**（`Side` 默认 LEFT→RIGHT；用户选定只改代码默认，已显式
保存过的配置文件不迁移）。左缘档的"竖条成线"公式保留。
- **光圈生硬修复**（用户报"没有跟随动画、没有淡入淡出"）：微光 alpha 增加入场淡入
斜坡（rise 0.45→0.85 线性爬升，此前过 0.5 直接满亮弹出），光圈矩形跟随内容位移；
退场淡出由 nvgGlobalAlpha 覆盖，本就正常。
- **拆类**：`NvgCardContent`（内容路：图标现渲/名字/数量/滚动框）从 NvgCardPainter
拆出（736→约 500 行）；`ConfigRows`（行模型：Row/行距/控件几何/逐行摆放）从
PickupCardConfigScreen 拆出。行为零变化。
- **英文文案全量重写 + ZH 微调**：统一 "Click to cycle" 句式与术语（rarity bar /
glow / highlight），压缩长度；**静音名单说明不再承诺压声音**——`Decision.muted`
的压制终点从未实现（全 mod 无音频代码），文案改为真实行为（弹卡但不强调），
功能缺口记入审计第 12 节；ZH 修掉"强调地静音弹卡"等拗口句。
- **清理**：`recovered/0.1.0` 删除（用户过目批准）；`StyleModel.f()` 死 helper 清除；
build.gradle 的 RarityCore 仓库注释改口（compileOnly 方案 → 反射定案）。
- **验收**：全部单测绿；hud 稳态 + 扫光时窗（常规/镜像两轮）+ 英文 config 五页
截图亲眼复核（镜像布局/方向/文字可读性/说明带净空逐条过）；性能读数正常
（layout 88us / paint 789us @5 卡）。

**英文界面收尾 + RarityCore 联动 + 稀有特效 + 性能护栏（09-20，第八次就地替换）**

- **英文溢出真正修完（上一轮修的是行标签，这轮才是用户指的位置）**：①每页底部说明
条原先宽度预算不对称（右缘只留 4px）且不补省略号，英文一长就顶到屏幕边被硬切，
还压在快捷栏格子里——改为左右对称边距、截断补 `…`、上移出原版 HUD 区，EN 页面
说明整体重写紧凑；说明条浮在滚动内容上时会文字相撞（外观页实测），给布局恢复
`BOTTOM` 预留带（注释即不变量：`ConfigLayout.BOTTOM` 22→43，三列在说明带上方
收尾）+ 说明带自己垫 94% 底色，游戏内压在世界方块上也读得清。②过滤页输入框的
占位文案比框宽，溢出部分被视口裁剪、框右缘残留半个字母（截图里那粒"神秘竖点"
就是 `and` 的 a）——`NvgTextField` 占位与值全部改走宽度约束缩字。③预览卡名字
预算少减一个 `gap`，长名样例的 "…+1" 贴死框缘——`PreviewStage` 面板预算多让出
一个 gap，夹窄不再发生。④`"1 rules"` 单复数语法错——`filter.count.one` 分键
（MC 1.20.1 语言系统无复数支持），两端键集仍一致。EN 的页面说明、过滤语法提示
同步紧凑化；harness 补外观页翻拍步骤，五页英文全量截图复核。
- **RarityCore 联动（自动接管）**：装了 [稀有度核心] 时，竖条/微光的强调色由它的
七档 + 玩家自定义色接管（`compat/RarityCoreBridge` 反射桥：`ModList` 守卫 +
MethodHandle 懒解析，失败或没装都完整回落 vanilla 四档主题色，渲染路径零感知）。
交接面是 layers 层新接口 `rarity/LinkedRarity` + `RarityAccent.setLinked`，与
`CardStage.setSources` 同款注入模式——不引编译期依赖，CI 无 jar 照常构建。
联动同时在统一档位尺（1~7）上给特效阶梯供货；档位名不上卡。
- **稀有特效两项**：①高稀有入场扫光——卡停稳后一道白色光带从卡面扫过一次（主题
`material.shimmerAlpha`，0=关，默认 90；vanilla rare(3)+ / RarityCore legendary(5)+
触发，一次性非循环，窗口接在入场之后）。②微光按档阶梯——稀有度微光从"有/无"
改为按统一档位爬强度（rare 0.45 起每档 +0.15，上限 1；经验卡与白名单强调卡维持
原满强）。两者纯 NanoVG 矢量，不进每帧分配。
- **性能（测量驱动）**：退场诊断日志 INFO→DEBUG 且仅 debug 开启时拼串（退场期每帧
两次 `String.format` 出热路径）；图标直画稳态免 `gui.flush()`（仅淡出/裁剪帧清批
—— 一摞 N 张卡一帧省 N 次整批提交）；y 翻转矩阵静态复用。新增 `paintMicros`
测量（与 layoutMicros 同款）+ harness hud 读数打印 + 硬预算护栏（4 卡以上
layout≤500us / paint≤4000us，超线 ERROR）。基线（harness dev 环境，5 卡稳态）：
layout 50us / paint 906us，余量充足。
- **全量审计**：`docs/audit-2026-09-20.md`——架构分层/类职责/死代码/测试/i18n/文档/
打包链/性能八维带证据盘点。重要反转：`platforms/1.20.1-legacy` 是 09-17 拍板保留
的参照实现（targets.json `_reference` 段），不删；`recovered/0.1.0` 是恢复过程的
中间产物、不在任何声明里，列入删除候选待过目。高收益拆类候选（ConfigScreen 行模型、
NvgCardPainter 内容路）记入 backlog，本轮不动刀。
- **验收**：全部单测绿；英文 config 五页 + HUD 稳态/扫光时窗截图亲眼复核
（1 rule 单复数、占位缩字入框、无裁剪残点、说明带独立净空、扫光带命中高稀有卡、
微光阶梯按档可见）；联动守卫日志（未装 RC → "稀有度色走主题"）确认。

**图标治本：删离屏贴图缓存，每帧原版现渲 + 退场换渲染层（09-19 深夜，第七次就地替换）**

- **四连症状一个根**：用户复核第六次产物报"图标上下颠倒 + 方块类分辨率低有锯齿感 +
动效丢失 + 整体亮度变暗"。四者同根 = 第五次引入的离屏贴图快照：快照冻住了原版
物品渲染里"活"的东西 —— 附魔光（glint）是滚动动画纹理，烘一次就丢（这就是"动效
丢失"与"变暗"：glint 亮条纹已不在贴图里）；方块等距 3D 渲染被钉死在低分辨率贴图上
再最近邻放大（锯齿）；读回像素的行翻转与投影方向叠加成整图镜像（颠倒）。
- **治本 = 不再快照**：`ItemIconCache` 整类删除（渲染目标/读回/行翻转/预乘 alpha/
缓存淘汰全链）。图标永远每帧走原版 `ItemRenderer.render` —— 附魔光动画、物理分辨率、
原版亮度、原版方向全是本尊。退场淡出的真正病根（实体渲染层不开混合）改为正面修复：
新类 `FadingItemBuffers` 在 alpha<1 的帧里把 `entitySolid`/`entityCutout`/
`entityCutoutNoCull` 重映射到开混合的等价层提交，`setShaderColor` 的 alpha 从此
数学上生效 —— 三档退场（淡出/火车退回/拉幕收拢）图标都是**真 alpha 淡出**，
且附魔光在退场途中仍在滚动。查不到的自定义层原样直通（后果仅是"该物品退场不淡化"，
不会错画）；alpha 字节 <4 的帧整帧不画图标（防末帧闪回）。
- **验收**：150 单测绿；harness 退场连拍逐帧实测图标亮度 70.2→46.0→44.7→35.7（收敛于
背景，单调无回弹、无变黑）；附魔物品夹具改为真附魔组件（glint 动画从此可验）。
- **同批修掉的英文界面问题**：色块控件的「主题/无效」标签原先硬编码中文（切英文直接
露出中文），已入语言文件（Theme / Invalid）；页签「Placement & stacking」穿出胶囊
叠到配置列、行标签「Reset this page」被截尾、编辑场副标题出画布 —— 三处改为超宽
整体缩字（新增左对齐版 `textFitted`）而不是裁掉；预览舞台的样例卡名原先显示
`pickupcard.config.sample.l…` 的 key 原文（`Component.literal` 吃掉了 key），
改 `translatable` 后正常显示。

**图标几何修复 + 配置界面全量语言化（09-19 深夜，第六次就地替换）**

- **图标"错乱"根治（离屏贴图缓存的几何账）**：用户复核第五次产物报"图标错乱、
甚至不如上一版"。根因是离屏贴图的映射不是整数：32px 渲染目标配 ±12 逻辑px 窗口
= 1.33px/格，物品在贴图里占 0.167~0.833 区，而卡面图案的填充矩形只采样
0.25~0.75 区 —— 图标被放大约 1/3、四周各切掉约两个素材像素。现在渲染目标 64px
配 ±16 窗口 = **2px/格整数超采样**，物品正好占贴图中央一半，与既有图案数学严格
对齐；贴图再按 `NVG_IMAGE_NEAREST` 最近邻上传（物品是像素画，原版 renderItem
就是最近邻），清晰度与原版同档。缓存上限 128 条也从注释落到了代码（超了整表清）。
- **配置界面全量语言化**：界面文案（页签/小节/行名/悬停说明/按钮/枚举值/样例名/
过滤页/拖拽编辑场，约 150 条）全部迁入语言文件，zh_cn 原文照旧、en_us 全新补齐；
代码里一律 `I18n.get`（shared 层用 `Component.translatable`），并新增单测钉住
两份语言文件 key 集一致 + 占位符数量一致。硬编码的开关文案「开/关」一并入册。
- **标题左对齐**：配置界面标题（含副标题）从居中改为靠左，与标签列同一起点；
状态行保持右端。
- **顺带修掉（语言化暴露的两处布局挤压）**：样例切换行从等分宽改为按文案量宽、
装不下整行按比例压；按钮/芯片文字超宽时按盒宽整体缩小（`NvgUi#textCenteredFitted`）
—— 等分布局是按中文两字标签定的，英文一上就互相叠字，harness 英文截图抓到。
已知极限：极窄画布（harness 1280x720@3x 那档）英文芯片文字会缩到偏小，但不重叠。

**图标淡出根治：物品图标改为离屏贴图（09-19 深夜，第五次就地替换）**

第四次把图标淡出改成"延迟融合"后用户复核仍报"拉幕收拢和淡出下图标慢慢变黑然后
消失"——hunt 纪律判定：时序修复是症状级，根因在渲染层能力：方块/mod 物品走
NO_BLEND 渲染层，alpha 分量无效，**在原版 renderItem 直画这条路上图标数学上不可能
淡出**（lj 的"全局调制"之所以有效，是因为它的图标本来就是自绘贴图，绕开了这条
路）。根治照搬同一思路：物品图标首次渲染时渲进一张透明底小贴图（`ItemIconCache`
离屏缓存），之后当带 alpha 的 NanoVG 图像画 —— 与卡壳同帧同变换同裁剪同全局
alpha，三档退场（淡出/火车退回/拉幕收拢）图标都是**真淡出**，"变黑曲线"整条删除。
逐帧实测（4800ms 慢放）：图标亮度与卡面全程同步衰减（100→44 vs 103→37），
尾段图标=卡面=背景三者收敛；图标视觉（立体模型、附魔光）与原版一致。

**淡出时序与预览缩影（09-19 深夜，第四次就地替换）**

- **图标淡出改"延迟融合"**：原地淡出下，方块/mod 物品的图标受渲染层无混合的限制，
做不到真透明 —— 旧曲线全程跟随卡壳衰减，观感是"图标逐渐变黑然后瞬间消失"。
现在前 15% 退场图标完全原亮（卡壳还厚、托得住），15%~50% 快速沉入卡面
（此刻卡壳也在大潮式衰减，读到的是"整卡在消失"而不是"图标在变黑"），
50% 后恒为融合态 —— 摘卡时无残影可留。逐帧实测：图标亮度 167→163（前段恒定）
→90（沉入卡面）→与卡面色差 ≤8/通道（融合完成）。三种消失方式共用这条改进
（火车退回=亮着退回去而不是边退边黑）。
- **「位置与堆叠」页的预览换成整屏等比缩影**：用户 grill 定案"预览要和游戏里的位置
对得上"。整块屏幕按面板宽等比缩进预览（HUD 带画暗示线），卡按真实的锚点/对齐/
同屏上限/自动缩放公式排在缩影里 —— 与游戏同一条排布公式，位置一眼对上；
放不下的张数缩影里也不画（和游戏的容量门同一句实话）。取舍：缩影里的卡很小，
看卡的长相去别的页（维持大特写卡）。**点缩影 = 直接打开整屏拖拽编辑场**。
- 顺带修掉：缩影边框一度画成横跨半屏的灰块 —— `GuiGraphics.fill` 的签名是两个
对角点 (x1,y1,x2,y2)，按 (x,y,宽,高) 传就会把"宽高"当"对角点"。

**打包：与 UI Deck 同装不再炸（09-19 深夜，第三次就地替换）**

两个 mod 都把 NanoVG 绑定的类摊平在 jar 根，同装时 JPMS 模块系统因"导出同一个包"
直接拒绝启动（148-mod 实例实测）。现在开发期照旧摊平（dev 类路径），发布产物改由
JarJar 嵌套 `lwjgl-nanovg 3.3.1`（嵌套 jar 自成模块、多 mod 同库按版本去重），
四平台 native 仍留在主 jar 作为资源供 LWJGL 自取。修复记录正本：
`docs/architecture.md` 的「打包：nanovg 走 JarJar 嵌套」一节。

这一版打包了 09-19 的两批反馈（第三批 9 条 + 第四批 5 条）+ 当晚验收的五处修复：

- **换页动画删除**：配置界面切页不再淡入/上滑，瞬间换内容（用户拍板"不要动画了"）。
- **行对齐三处**：标签文字与按钮/悬停带共用一条中心线（从前低 1px，悬停带"套不正"）；
小节头的强调刺与自己的字对齐；开关的"开/关"在轨道左侧居中。
- **预览静止卡在面板里居中**（从前贴在空面板底角），且非动画页永不播入场动画。
- **拖拽编辑场全屏幕随便拖**：删掉自造的限制圈（从前屏幕底部/右缘一整条拖不动），
游戏侧也不再自动抬锚点——拖到哪儿就是哪儿。
- **编辑场的示例与区域框跟着「水平对齐」档走**（从前右缘对齐也按左缘画，差一整卡宽）。
- **「消失方式」默认改为「火车退回」**——与入场对称；淡出、拉幕收拢仍在，随时可换回。
- **方块类/mod 物品的图标退场回弹根治**：这类物品的渲染层无混合（NO_BLEND），
从前只压 alpha 等于没压，图标全程满亮、摘卡瞬间才消失；现在图标随卡一起向卡面
底色靠拢（暗下去），不再有最后一帧的凭空闪现。

**架构重构（09-19 深夜，第二次就地替换）**

用户复核时又报四个问题（入场设置不在动画页 / 预览卡片重叠且退场不播 / 锚定配置
失效、拖动有死角 / 恢复默认把设置改错），全量审计定案：**不是四个新 bug，是同一件
架构病** —— 配置界面 1735 行身兼十二职，同一件事的真相存了三四处，各自漂移。拆法：

- **配置项注册表 `ConfigPageSpec`**：每个配置项一行声明 —— 归哪页、哪个小节、控件
怎么造、默认值是什么，全项目只有一个出处。「展开方式」（火车/拉幕）从位置页搬回
**动画页**（它是入场的形态）；「恢复本页默认」改写 `键.getDefault()`，不再手抄
三十多个字面量 —— 消失方式曾被抄成旧默认"淡出"，点恢复反而改错设置，这条路封死。
页面归属与默认值第一次有离线单测（`ConfigPageSpecTest`，6 项）。
- **预览舞台 `PreviewStage`**：预览卡的 key 从前同一样例共用一个（"preview:长名"），
而换位平滑按 key 记账 —— 舞台上同时有两张同款卡就互相抢同一个目标，
**全部钉在同一个 y 上**，这正是"预览卡片重叠"；自动节拍又见同款只并数字，
舞台永远只有一张卡、"三张真卡的舞台"名不副实。现在每张卡出生领唯一序号
（preview#N），节拍改成"三并一新"：重叠在结构上不可能，入场→停留→消失
一整条链自动演完。
- **拖拽编辑场所见即所得**：从前编辑场、游戏排布、存盘的锚点三层各夹各的
（"离右缘一个占地宽" vs "离右缘一个卡宽" vs 不夹），拖到头堆不动、设了没反应。
现在样例堆直接走游戏同一条 `StackLayout.stack`（同公式、同夹取）：堆停下的地方
就是游戏里画的地方；锚线单独画出来，被边距夹住时锚线还在动，屏幕上明说
「卡已贴边距：锚线再往右，卡也不会越过屏幕边距」。抓取改成以卡为基准
（从前以锚线为基准，卡被夹住后有死区）。
- **界面类瘦身**：`PickupCardConfigScreen` 1735 → 1032 行，只剩页面状态、行摆位、
绘制、事件分发、harness 入口；过滤名单（`FilterPageBuilder`）与预览（`PreviewStage`）
各自独立。功能一个没少，五页、恢复默认、过滤增删、harness 自动化全部回归通过。

这一版打包了 09-19 的两批反馈（第三批 9 条 + 第四批 5 条）。核心是把卡堆搬家这件事
一次做对：**卡片现在贴着物品栏上缘那条固定底线出现、旧的向上顶**——常见分辨率
（854×480 窗口的 427×240 画布）从此真能同时放满 5 张原尺寸的卡。

**位置与堆叠（第四批）**

- **底锚回归**：默认锚线 = HUD 带上方（让开动作栏提示语那一整条）。新卡永远出现在
这条线上、旧的被顶上去（340ms 平滑）。从前默认锚在准星下方，常见画布上锚点以下
只剩 33px，5 张卡要 116px —— 自动缩放被迫永远激活，放不下的卡还会被**硬切消失**。
- **放不下改排队，不再丢卡**：几何上放不下的拾取退回队列排头（先回先上），位子一空
第一个回来；不会再有"捡了东西屏幕上却无声蒸发"。
- **缩放与卡宽加了过渡**：自动缩放从前按张数一档一档跳（100%→75%→60%），退场播完
那一刻整摞卡瞬间放大一圈 —— 就是"动画结束时的图标回弹"。现在三样（位置、缩放、
卡宽）走同一条 340ms 曲线。
- **切换"右缘对齐"不再瞬移**：自动锚线按对齐档各自解析（左缘档=竖条成线的老公式，
右缘档=贴右边距），从前右缘档错拿左缘的数，切一档卡就跳到屏幕中左。

**配置界面（第四批）**

- **固定行距 + 小节头**：行距不再随页内行数变（从前换页时行会各自漂）；页内按
「显示什么」「形状」「颜色」等分组。
- **每页第一行「恢复本页默认」**：改动立即生效的后悔药。位置页不重置锚点
（那是编辑场「回到默认」的事）；过滤页=清空三张名单。
- **颜色改色块**：点色块在色板里循环，第一档永远是「跟随主题」；手打 hex 仍可写
TOML。填错的值会明说"无效"，不再安静地画成主题色。
- **预览按页分工**：非动画页画一张静止完整卡（点预览/换样例重播一次入场，贴面板底），
动画页保留三张真卡的自动舞台；「来一张」必出新卡（从前同款会被合并吞掉，像点了
没反应）。
- 其余：滚动位置在删规则后不再跳回顶部；循环按钮的悬停说明写全档位顺序；长名字
在预览面板里按面板宽度截断（从前会戳出卡壳）。

**动画与术语（第三批）**

- **消失方式三选**：淡出（原地变透明）/ 火车退回（内容平移回竖条后）/ 拉幕收拢
（可见范围从右往左收），与入场方式自由组合，三种都叠加透明度下降。
- **右缘对齐**回归：右缘齐、左缘随卡宽参差（HTML 草稿的「右边缘对齐」预设）。
- **微光呼吸**：经验卡/白名单卡的微光按正弦往复（1.6s 周期，每张卡错开相位），
可在主题里关。
- **图标末帧闪修复**：淡出最后一帧图标闪回全亮 —— 根因是堆叠数/耐久条的绘制排在
队列里、透明色复位之后才提交。现在复位前先冲一次队列。
- 界面术语换成草稿短词版：火车 / 拉幕、竖条左缘锚定 / 右缘对齐、图标内边距、
底色（上/下）；完整解释只进悬停提示。

有 `BodyGeometry` 类与 `NvgCardPainter#warmUpStep` 方法（且 lang 内 `row.mirror.name` 键
仍在）= 第十次后。

----

**Mirror entrance direction + first-pickup stutter (09-20, 10th in-place replacement)**

- Mirror cards animate the right way now. The shell and the content path each pushed the mirror coordinate, and the content path had the exit direction multiplied in — so the shell slid out from the bar while the text and icons appeared from the opposite side. Invisible at rest, only visible mid-animation. Both paths now share one geometry (`BodyGeometry`); displacement only adds, and unit tests pin it
- First pickup no longer stutters. Layered timing measured 42ms on that one frame: shell first draw 23ms (the driver only compiles its shaders on the first real draw), icon first render 27ms, text measurement 10ms. All of it is now amortised over 7 frames after joining a world, leaving 1.6ms on the first pickup and dropping the stack-peak frame from 53ms to 7.8ms
- Readable perf telemetry on the render path: per-frame peak, a slow-frame probe (one context-carrying log line above 4ms, active in release builds too) and a pickup accounting probe

**Mirror cards + right-edge default + halo + class split (09-20, 9th in-place replacement)**

- Mirror card option: one switch moves the bar to the card's right edge and runs the row right to left — icon, then name and count — with entrance and exit animations mirrored too
- Factory default alignment is now right edge (right edges flush, left edges ragged); saved settings are untouched
- The rarity halo fades in with the card and follows the content instead of popping in
- English config copy rewritten for consistent wording and terms; the mute-list description corrected
- The config screen and the render entry were split into smaller classes with no behaviour change

**English UI finish + RarityCore + rarity effects + perf guardrails (09-20, 8th in-place replacement)**

- Per-page hint line no longer runs off the screen edge (symmetric margins, ellipsis on overflow, clear of the hotbar, its own backing band); the filter placeholder scales inside its box; the long-name sample's count no longer touches the border; the "1 rules" grammar slip is fixed
- RarityCore integration: with it installed, the card bar and the glow use your seven-tier RarityCore colours with no setup (reflection-based); without it everything falls back to theme colours
- Two rarity effects: high-rarity cards (rare+ vanilla, legendary+ RarityCore) get a one-shot light sweep after settling, and the rarity glow climbs a per-tier intensity ladder
- Performance: hard budget guardrails on the render path (5 settled cards measure ~0.9ms) plus three hot-path cleanups

**Icons rebuilt: no more offscreen snapshot, render every frame (09-19, 7th in-place replacement)**

- Four symptoms, one root cause: upside-down icons, blocky low-resolution blocks, lost animation and a darker look. All four came from the offscreen texture snapshot introduced in the 5th pass — a snapshot freezes everything live in vanilla item rendering: the enchant glint is a scrolling texture (baked once, it is gone — hence the lost animation and the dimming), the isometric block render was pinned to a low-resolution texture and nearest-neighbour upscaled (jaggies), and the read-back row flip stacked with the projection direction into a mirrored image (upside down)
- The fix is to stop snapshotting: `ItemIconCache` was deleted whole (render target, read-back, row flip, premultiplied alpha, eviction). Icons now go through vanilla `ItemRenderer.render` every frame, so glint, physical resolution, vanilla brightness and orientation are the real thing
- The actual root cause of the fade was the solid render layer, and it is now fixed head-on: the new `FadingItemBuffers` remaps `entitySolid`/`entityCutout`/`entityCutoutNoCull` onto their blended equivalents on any frame with alpha < 1, which makes `setShaderColor` alpha work mathematically. All three exit modes now fade icons in true alpha, with the glint still scrolling as they leave. Unknown custom layers pass through untouched (the only consequence is that item not fading), and frames below 4 alpha bytes draw no icon at all (guards against an end-of-fade flash)
- Verified by 150 green unit tests and a frame-by-frame harness capture: icon brightness 70.2 → 46.0 → 44.7 → 35.7, converging on the background, monotonic, no rebound and no darkening
- Same pass: the swatch control's "theme / invalid" labels were hardcoded Chinese and leaked into the English UI (now in the language files); the "Placement & stacking" tab, the "Reset this page" row label and the edit-stage subtitle overflowed instead of scaling (now shrunk as a whole, with a new left-aligned `textFitted`); the preview stage printed a raw translation key because `Component.literal` ate it

**Icon geometry + full config localisation (09-19, 6th in-place replacement)**

- The offscreen mapping was not integral: a 32px render target with a ±12 logical-pixel window is 1.33px per unit, so the item occupied 0.167–0.833 of the texture while the card art sampled only 0.25–0.75 — the icon was magnified by a third and lost about two source pixels on every side. The target is now 64px over a ±16 window (2px per unit, exact integer supersampling), the item occupies exactly the middle half and lines up mathematically with the existing art, and the texture uploads with `NVG_IMAGE_NEAREST` (item art is pixel art and vanilla `renderItem` is nearest too). The 128-entry cache cap also moved from a comment into code
- Every config string (tabs, sections, row labels, tooltips, buttons, enum values, sample names, the filter page, the drag editor — about 150 of them) moved into the language files, Chinese unchanged and English newly written; the code goes through `I18n.get` (`Component.translatable` in the shared layer), and new unit tests pin the two files to the same key set and the same placeholder counts
- Config title (and subtitle) went from centred to left-aligned with the label column; the status line stays right-aligned
- Two layout squeezes the localisation exposed: the sample-switch row now measures its text instead of splitting evenly, and button/chip text scales down as a whole when it overflows (`NvgUi#textCenteredFitted`) — the even split had been sized for two-character Chinese labels and overlapped as soon as English appeared. Known limit: on very narrow canvases the English chip text shrinks noticeably, but it no longer overlaps

**Icon fade-out root cause: move icons to an offscreen texture (09-19, 5th in-place replacement)**

- The 4th pass changed the fade to "delayed blending" and the report came back unchanged: under wipe and fade the icons slowly went black and then vanished. Time-series fixes were symptomatic; the root cause was a rendering-layer limitation — blocks and mod items use a `NO_BLEND` layer where the alpha component does nothing, so on the direct vanilla `renderItem` path an icon mathematically cannot fade out
- The fix copies the approach that does work: render the item into a small transparent-backed texture on first use (`ItemIconCache`) and draw it afterwards as an alpha-carrying NanoVG image — same frame, transform, clip and global alpha as the shell, so all three exit modes fade icons for real and the whole "darkening curve" was deleted
- Frame-by-frame measurement (4800ms slow motion): icon brightness tracks the shell all the way down (100 → 44 against 103 → 37), the icon, shell and background converge at the tail, and the icon's look (3D model, enchant glint) matches vanilla

**Fade timing and preview thumbnail (09-19, 4th in-place replacement)**

- Icon fade-out became "delayed blending": for the first 15% of the exit the icon stays at full brightness (the shell is still thick enough to carry it), from 15% to 50% it sinks into the card face (so what you read is the whole card leaving, not the icon going black), and past 50% it stays blended, leaving no afterimage when the card is removed. Measured: 167 → 163 (constant) → 90 (sinking) → within 8 per channel of the card face (blend complete). All three exit modes share the improvement
- The placement page's preview became a whole-screen scale model: the screen is scaled into the panel, the HUD band is hinted, and cards are laid out by the real anchor/alignment/cap/auto-scale formula — the same formula the game uses, so positions match at a glance, and cards that would not fit are not drawn either (the same honest answer as the game's capacity gate). Trade-off: cards in the model are small, so use the other pages for a close look. Clicking the model opens the full-screen drag editor
- Also fixed: the model's border briefly drew as a half-screen grey block — `GuiGraphics.fill` takes two opposite corners, and passing (x, y, width, height) makes the width and height act as the second corner

**Packaging: no crash when installed alongside UI Deck (09-19, 3rd in-place replacement)**

- Both mods flattened the NanoVG bindings into the jar root, and with both installed the JPMS module system refused to launch over the exported package clash (measured on a 148-mod instance). Development still flattens them (dev classpath), but the release artifact now nests `lwjgl-nanovg 3.3.1` through JarJar (a nested jar is its own module, and duplicate libraries dedupe by version), with all four platform natives left in the main jar for LWJGL to pick up. Written up in `docs/architecture.md`

**Architecture refactor (09-19, 2nd in-place replacement)**

- Review turned up four problems (entrance settings not on the animation page, preview cards overlapping and not animating their exit, anchor configuration not taking effect with dead zones when dragging, restore-defaults setting the wrong values). A full audit found one architectural illness rather than four bugs: the 1735-line config screen held twelve jobs and stored the truth about one thing in three or four places that drifted apart
- Every option is now one line in a registry (`ConfigPageSpec`) — page, section, control, default — with a single source of truth in the project, offline unit tests for page ownership and defaults, and "restore this page" calling `key.getDefault()` instead of hand-copied literals (the exit mode had been copied as the old default, so restoring made things worse)
- The preview stage keys each card uniquely (`preview#N`) instead of sharing one key per sample, so cards can no longer fight over the same animation target and overlap; the auto beat is "three merge into one new", so entrance, dwell and exit play out as a whole chain
- The drag editor is now WYSIWYG: samples run through the game's own `StackLayout.stack` (same formula, same clamping), the anchor line is drawn on its own and keeps moving while clamped by the margin, and the screen says out loud when a card is pinned to the margin

**Placement and stacking (4th batch)**

- Cards anchor to a fixed line above the hotbar and stack upward: on the common 427x240 canvas all five cards finally fit at 100%
- Overflow queues instead of vanishing; auto scale and card width glide through a 340ms transition (fixes the end-of-animation icon rebound)
- Switching right-edge alignment no longer teleports the column

**Config screen (4th batch)**

- Fixed 20px row rhythm with section headers, a per-page "restore defaults" row, colour swatches with "follow theme" as the first stop, and scroll position kept across list edits
- Per-page preview: a static full card elsewhere, the live stage on the animation page

**Animation and wording (3rd batch)**

- Exits get three modes (fade / train-back / wipe) freely combinable with entrances
- Rarity glow breathes; the last-frame icon flash is fixed
- UI wording shortened to the draft's short terms; the full explanations live in the hover tooltips

## v0.2.1

> **注意：这一段的 jar 被就地替换过一次（2026-09-18 傍晚）。** 用户要求"不改版本"，
> 所以 `mods.toml` 里仍是 `0.2.1`，但**分发的 jar 已经包含 09-18 下午的全部修复**
> （下面"### 09-18 修复"那一节）。旧产物 `sha256 55c86931…`，现行产物
> **1173391 字节、`sha256 98e40f7b7ef4ccf1…`**。判据：包里有没有 `HudSafeZone$Strip.class`。

**09-18 修复（同一份 0.2.1 产物内）**

- **卡片位置搬进"物品栏与屏幕右侧之间那块区域"**。此前一直放在 HUD 带<b>上方</b>，
理由是"那条缝只有 76px、放不下一张卡" —— 而那个 76 是照**左撇子**的副手位置算的
（原版 `Gui#renderHotbar` 第一句就是 `getMainArm().getOpposite()`，右手玩家副手在左）。
右手玩家实际有 106px。现在整列落在 `x ∈ [快捷栏右缘, 屏幕右边距]`，并且**一路下到屏幕底、
与快捷栏同层**；条带装不下（连"竖条+图标+数量"都放不下）才回退到原来的位置。
- **"再次拾起"的动画原来根本不存在**：`bumpMs` / `bumpEnabled` / 配置界面那格"数字跳动"
从第一版起就是**死参数**（`CardCanvas#bumpOf` 一次都没被调用过）。现在真的会动：
整张卡脉冲一下 + 数字从旧值滚到新值。
- **淡出末段被拾起时"文字和图标突然闪一下"**：救回的不透明度是从"已经淡到哪儿"补回来的，
而淡出末尾那里是 alpha ≈ 0.01 —— 一张看不见的卡冲回全不透明。淡回时长原来硬编码 160ms、
淡出是 480ms（回来比离开快 3 倍）。现在淡回 **300ms 且进主题可调**，
并且**淡到 alpha < 0.15 才被拾起的不再救回，改播入场**（那时它已经不在屏幕上，
玩家刚按下拾取，本来就该看见"东西进来了"）。
- **配置预览里四档颜色全是灰的**：「稀有」那格用的是**钻石剑**，而钻石剑在原版是 `COMMON`。
现在四格各带真实稀有度（石头 `COMMON` / 金苹果 `RARE` / 下界之星走专用绿 / 附魔金苹果 `EPIC`），
且开屏时会拿 registry 读到的稀有度核对一次、不符就在日志里报 ERROR。

**Changed**

- **渲染收口成 NanoVG 矢量一条路**：竖条 / 两个框 / 微光 / 入场裁剪全在 NanoVG 里；
物品图标与中文文字仍走原版批次（那是 MC 自己的物品模型与字形图集，不是第二种画法）。
SDF 形状层、SDF 整卡回退、`CardPainter` 插槽接口**全部删除** —— 引擎起不来就整帧不画
并留一条 ERROR，不留"只在别人机器上跑得起来"的第二实现。
- **配置界面重铸**：三列（标签列 / 配置列 / 预览列）、一行一项、可滚、悬停一句人话；
退让顺序 = 先收预览 → 标签挪顶上 → 配置列永远在。控件也是自绘的（圆钮、细轨道
原版九宫格画不出来）。
- **卡片尺寸 32 → 20 逻辑px**：MC 字体固定 8px，框比字大就是"又大又空"。
底部留白不再是魔数 —— 按原版 HUD 矩形算出来，让到动作栏提示语那一行之上。
- **入场 800ms**：竖条自上而下长、内容从隧道口滑出，曲线取 Material 标准曲线
（原来那条 t=0.25 就走 0.757，看着像冲出来）。
- 配置项**没有「跟随主题」第三态**了：界面显示生效值，拨过哪项就写死哪项。

**Added**

- **过滤页**：黑名单 / 白名单 / 静音名单三张表终于能在游戏里加删 —— 此前只能手改 TOML。
写法三种：`minecraft:cobblestone` / `#forge:ores` / `@modid`。
- **卡片缩放**：自动档按"一摞卡塞不塞得进 HUD 带之上"整体等比缩，下限 60%。
- **合并粒度四档**、**同屏上限 + 排队上限 + 溢出卡**（屏满先排队，不再顶掉旧卡；
排满并成一张"还有 N 项"）。
- **主题数据化**：几何 / 材质 / 文字 / 强调色 / 动画全部来自主题 JSON，资源包可整套覆盖。
强调色现在**真的**从主题取（见 Fixed 第二条）。
- 无人值守跑测 harness：自己进世界 → 注入样例 → 操作 → 截图 → 自己退出。

**Fixed**

- 配置界面**标签列整列没画**（控件在、也能点、单测全绿，就是没画）。顺带让这一类
漏画自报：控件自己记"这帧画过没有"，harness 报 `画了=14/14`，漏画直接 ERROR。
- 窄画布上**底部的悬停说明与「预览已收起（窗口太窄）」两段字相撞** ——
320×240（真玩家到得了）就已贴死，256 宽时重叠 67 逻辑px 糊成一团。
- **主题里的强调色是死数据**：`tokens.css` 与两份主题 JSON 从第一天就写着 `accent.*`，
但没有任何代码读它，Java 里另有一份硬编码 —— 改主题"改了没反应"。
- 部分**滑条拖不动**（值供给器读的是打开页面那一刻的快照）。
- **淡出最后一帧图标与文字完全不透明再消失**（同名重拾把淡出那张整张换掉）→ 改成"淡回"。
- harness **从来没能自己进世界**、HUD 模式**拍完不退出**（启动参数写错块 + 收工分支不可达）。

**与 0.1.0 的关系**

0.1.0 是 ApricityUI 硬前置的版本（HTML/CSS 卡面），已打 `archive/aui` tag 归档。
本版是**另起一条线**：零依赖自绘，按 `docs/design.md` 的定案重做，不向下兼容 0.1.0 的配置。

首个**自绘**版本（0.2.0 只在开发中迭代过，没发布）。**不再需要 ApricityUI 前置** ——
卡面从矢量到排版全部自己画，零必需依赖。

## v0.1.0


**Added**

- 纯客户端嗅探原版拾取包（Mixin `ClientPacketListener#handleTakeItemEntity`），服务端无需安装。
注入点选在 `ensureRunningOnSameThread` 之后 —— 此时物品实体尚未移除，能拿到含改名与 NBT 的真实 ItemStack。
- 四档稀有度卡面（木牌 / 铜牌 / 蓝银 / 暗紫鎏金），装饰层数递增。
- 界面由 ApricityUI 渲染，外观与动画全部写在 CSS 里（改外观不用重编译）。
- 合并：窗口内连续拾取同类物品累加数量并触发数字跳动，而不是重复弹出。
- NEW 角标：本局首次遇到的物品亮一下。只记内存、不落盘。
- 附魔光效与耐久条由原版物品渲染承担。
- 配置：停留时长、合并开关与窗口、同时在屏上限、数量写法。
- 结构守卫 `tools/verify_targets.py`：矩阵规则 / 身份唯一 / 层谓词与挂载一致 / 工程与条目互存。

**与 0.1.0 之前的关系**

仓库重建于 2026-09-16。更早的 `D:\pickupnotice` 是自己写 SDF 着色器 + 烘焙位图贴图的路线，
因"烘焙贴图被三段拉伸导致细节糊、且设计稿与实现漂移"而整体重做，改为 ApricityUI + CSS。
老仓库以 `archive/terminal-card-wip` 标签留档，不再维护。

首个版本：拾取卡片提示的完整形态。

