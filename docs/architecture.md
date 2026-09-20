# 架构

单分支多目标：**代码按"能被哪些目标共用"分层，工程按"发到哪个加载器"分目录。**
声明全部在 `versions/*.json`，由 `tools/verify_targets.py` 与
`gradle/pickupcard-layers.gradle` 两处读同一份声明。

```
gradle.properties          ← 仓库身份唯一来源（mod_version / mod_id / …）
versions/targets.json      ← 有哪些目标、各挂哪些层、能不能发
versions/layers.json       ← 层的定义（每层是一个谓词）
gradle/pickupcard-layers.gradle  ← 按声明把 shared/ 与层接进源集
shared/                    ← 与平台、映射、加载器都无关的纯逻辑
layers/mapping/official/   ← 写给 Mojang 官方名的那一份
platforms/<目标>/          ← 各自的 Gradle 工程（注入配置、事件总线、入口）
tools/verify_targets.py    ← 结构守卫（CI 第一道闸）
```

## 代码放哪一层

| 想加的代码 | 放哪 | 判据 |
| --- | --- | --- |
| 队列规则、合并窗口、数量格式 | `shared/` | 不 import `net.minecraft` 就能编过 |
| Mixin 进原版类、读写 ItemStack | `layers/mapping/official/` | 用到官方名（Yarn 里名字不同） |
| 自绘控件（按钮/开关/滑条/输入框） | `layers/mapping/official/render/nvg/ui/` | 形状走 NanoVG、文字借 `GuiGraphics`，不绑加载器；只在"长得跟原版控件不一样"时才加新的 |
| 配置定义、事件总线、mod 入口 | `platforms/<目标>/` | 绑加载器，且目前量少不够成层 |

层名对应的是**类别**而不是具体版本：`official` 是所有用官方名的目标共用的，
`1.21+` 是所有 1.21 以上的目标共用的。所以 1.21.1 与将来的 1.21.x 都挂 `1.21+`。

## 为什么拾取逻辑与物品类型分开

`NoticeQueue` 的载荷是泛型 `T`，队列本身只认 `key` 与 `lookKey` 两个字符串。这样：

- 合并规则的**全部测试都不需要启动游戏** —— 载荷用 `String` 就够了（见 `NoticeQueueTest`）；
- 物品类型只出现在映射层，将来 Fabric 那份 Yarn 名副本不必重写队列；
- "队列说该有哪些卡"与"卡片怎么画"彻底解耦。

## 渲染层：一条路

渲染是需求变得最快的一层（换风格、换引擎、加特效），而拾取管线几乎不动。所以"怎么画"
被收口成**一个类**，其余各管一件事——上一版把这五件事连同绘制全塞进一个 436 行的类里，
改任意一处都要先读懂全部：

| 问题 | 答案在哪 | 性质 |
| --- | --- | --- |
| 一张卡怎么画（外壳：矢量） | `render/nvg/NvgCardPainter` | **唯一的画法**（NanoVG 矢量：竖条/框/微光/扫光） |
| 一张卡怎么画（内容：原版） | `render/nvg/NvgCardContent` | 图标原版现渲 + 原版字形（2026-09-20 从 Painter 拆出） |
| 三个框在哪（自然位置） | `shared/style/BodyGeometry` | **镜像坐标的唯一出处**：外壳与内容同吃一份（2026-09-20 第三轮；此前两路各推一遍 →「文字动画没镜像」） |
| 卡多大 | `render/CardMetrics` | 要字体，所以量文字宽度 |
| 卡在哪 | `shared/layout/StackLayout` | 纯数学，有单测 |
| 卡不能压到哪 | `shared/layout/HudSafeZone` | 原版 HUD 的矩形 + 底部留白，纯数学，有单测（含反例对照） |
| 主题从哪来 | `render/StyleSource` | 懒加载 + 一秒热重读 |
| 动画进度 | `shared/style/CardTimeline` + `render/CardCanvas` | 纯函数 + 每帧上下文 |
| 事件 → 屏上的卡 | `render/CardStage` | 只调度，**不画一笔** |

## 稀有度联动：一个交接面

卡片强调色的"档位→颜色"知识住在 `rarity/RarityAccent`（vanilla 四档 → 主题四槽）。
装了 [稀有度核心 RarityCore] 时，七档 + 玩家自定义色通过两层接管它：

| 角色 | 在哪 | 说明 |
| --- | --- | --- |
| 交接面 | `layers/.../rarity/LinkedRarity`（接口） | `tierOf`（0=无档位，1~7）+ `colorOf` + `showcaseFrom`（特效门槛） |
| 桥 | `platforms/.../compat/RarityCoreBridge` | 反射 + MethodHandle 懒解析；ModList 守卫；失败即解除联动 |
| 注入 | `PickupCard` 构造器 | `RarityAccent.setLinked(...)`，与 `CardStage.setSources` 同款模式 |

不引编译期依赖（CI 与开发机没有那个 jar）；卸载联动 = 不注入，渲染路径零感知。
统一档位尺 1~7（vanilla 四档占前四格）同时供特效阶梯（微光强度、入场扫光门槛）使用。

## 一次性开销：进世界时摊付

引擎里有一类钱是**一次性**的，而且全部记在"第一次真的用到"那一帧上：NanoVG 驱动
第一次真正画东西才编着色器（只建上下文不触发）、物品图标第一次现渲要烘焙模型、
`font.width` 第一次量宽触发字形按需栅格化。过去它们全落在**第一次拾取那一帧**，
实测合计 42ms（用户报"一捡东西卡一下"的出处）。现在由 `NvgCardPainter#warmUpStep`
在进世界后分 7 帧摊付（外壳两轮几何 → 字体量宽 → 图标每帧两个），`CardStage`
按帧推进它。规则：**"第一次"的判定由驱动说了算，预热必须按真实路径的原样顺序
再走一遍**（外壳在 NVG 帧内、图标在帧外；只建上下文、或把图标画进 NVG 帧，都不算数）。

**渲染方案已定案（2026-09-17）：NanoVG 矢量自绘。** 曾经的"可替换插槽"
（`render/CardPainter` 接口）连同它的第二实现一起删了：只有一个实现的接口不是接缝，
是留给下一个人踩的坑。出局者与理由见 [`decision-rendering.md`](decision-rendering.md)。

## 已知约束

- **纯客户端**：任何原版/别的 mod 的服务器都能用。代价是拿不到只有服务端知道的信息
  （物品实体上的改名与 NBT 例外——注入点在实体移除之前，能捞到真身）。
- **卡宽跟名字走**：`CardMetrics` 现量现算，所以画法必须适配可变宽度。
  "定宽贴图横拉"是上一版走不通的路（见 `decision-rendering.md`）。
- **同屏上限由账本管**：`maxOnScreen` 满员时淘汰最久没被碰过的那张，渲染层不参与取舍。

## 渲染路径：只有一条

"这张卡由谁画"**只有一个答案**（2026-09-17 收口）。这张表留着，是因为接手的人迟早会在
git 历史里翻到另外几个名字，得知道它们为什么没了：

| 路径 | 状态 | 在哪 |
| --- | --- | --- |
| **NanoVG 矢量**（微光 / 竖条 / 两个框 / 入场裁剪 / 扫光） | **唯一的生产路径** | `layers/mapping/official/…/render/nvg/NvgCardPainter` |
| 原版内容（物品图标 + 中文文字） | **活的，且必须有** | `render/nvg/NvgCardContent`（2026-09-20 从 Painter 拆出）—— 那是 MC 自己的物品模型与字形图集，不是"第二种画法" |
| DOM 草稿 | **活的，视觉真源** | `design/theme.css` + `measure.html` + `animation.html`：改外观**先动它** |
| ~~SDF 形状层~~ | **已删**（2026-09-17） | 原 `render/shape/*` + `assets/*/shaders/core/gui_shape.*` |
| ~~投影 + 顶部高光~~ | **已删**（2026-09-17，用户："直接把影子和高光删了"） | 参数整组从 `tokens.css` → 主题 JSON → `StyleModel` / `StyleOverrides` → 配置界面删掉，不是"默认设成 0" |
| ~~SDF 整卡回退~~ | **已删**（2026-09-17） | 原是 `TrioCardPainter.paint()/chrome()/barShapes()`：引擎起不来时降级用 |
| ~~`BaselineCardPainter`~~ | **已删**（2026-09-17） | — |

### 为什么删得掉（一次真实的账）

同一条卡几何曾经有 **3 个实现**（DOM / NanoVG / SDF 回退），于是每次改卡面都要改三处。
2026-09-17 修"内容穿透竖条"那个 bug 时，裁剪补进了 3 条绘制路径、**漏了第 4 处**
（NanoVG 的影子批没有窗口），用户第二遍才报回来；同一轮还发现 SDF 回退里**竖条被自己的
裁剪吃掉**。**同一个几何写 N 遍，就一定会有 N-1 遍是错的。**

【代价要认】**现在没有回退路径了**：NanoVG 的 native 起不来（没打进产物 / GL3 初始化
失败）就整帧不画卡，日志里留一条 ERROR 说明原因。这是刻意的取舍 —— 一份只在别人机器上
才跑的第二实现，比"少画"更像故障：连日志都不会有。所以发布前必须验的是绑定与四平台
native 都在 jar 里（`build.gradle` 的 `unpackNvg` 把它们摊进产物）。
