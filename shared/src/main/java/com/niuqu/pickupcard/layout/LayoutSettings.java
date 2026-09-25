package com.niuqu.pickupcard.layout;

/**
 * 卡片在屏幕上怎么摆、怎么出现。**行为参数，进 {@code config/pickupcard-client.toml}**，
 * 不进主题 JSON —— 判据是"换一套主题时，应不应该把它一起换掉"：换主题该换的是配色和
 * 质感，不该让卡片突然跳到屏幕另一边（那会被当成 bug）。
 *
 * @param appearMode   卡片出现时怎么展开。{@link Appear#SLIDE} = 内容保持原样从竖条后面平移出来；
 *                     {@link Appear#CLIP} = 内容不动、可见范围从左往右展开；
 *                     {@link Appear#BOUNCE} = 沿火车路线滑出、冲过终点再弹回（过冲回弹）；
 *                     {@link Appear#DROP} = 沿火车路线斜着滑进来，同时从锚线上方落下一记、落地带小弹。
 * @param exitMode     卡片怎么消失。{@link Exit#FADE} = 原地淡出；{@link Exit#TRAIN} = 内容整块
 *                     平移回竖条后面（与火车入场的逆放）；{@link Exit#WIPE} = 可见范围从右往左
 *                     收拢（与拉幕入场的逆放）；{@link Exit#FALL} = 向下坠 + 淡出；
 *                     {@link Exit#SCALE} = 整卡绕卡心等比缩小 + 淡出。各档都叠加透明度下降。
 * @param align        水平对齐基准。{@link Side#LEFT} = 竖条左缘贴锚线（一摞卡的竖条成一条线）；
 *                     {@link Side#RIGHT} = 卡片右缘贴锚线（设计里的「右边缘对齐」预设，
 *                     卡宽不齐时左缘参差、右缘齐）。
 * @param separation   两张卡之间的空隙（像素）。它跟卡内间隙（{@code style.gap}）不是一回事，
 *                     刻意分成两个键：一个是"卡与卡"，一个是"框与框"。
 * @param scalePercent 卡片缩放百分比（100 = 原样）。{@link #AUTO_SCALE} = 自动：放不下就缩小
 *                     （见 {@link #scale}）。它<b>不</b>属于主题：主题管长相，缩放管"塞不塞得下"。
 * @param anchorX      锚线的横坐标（<b>画布宽度的比例</b> 0~1）。左缘锚定时＝竖条左缘的位置；
 *                     右缘对齐时＝卡片右缘的位置。{@link #AUTO_ANCHOR} = 自动：<b>按对齐档各自解析</b>
 *                     （见 {@link #anchorLeft}）—— 同一条自动公式伺候两种语义是 2026-09-19 修掉的错：
 *                     从前右缘对齐拿"竖条左缘"的数当右缘用，切一档卡就瞬移到屏幕中左。
 * @param anchorY      卡堆锚点的纵坐标（<b>画布高度的比例</b> 0~1）＝ 第一张卡（最新）<b>顶边</b>的位置。
 *                     卡堆<b>向上生长</b>：新卡永远出现在锚线上、旧的被顶上去（2026-09-19 定案，
 *                     第三次回到"新卡固定一点"——前两次为顶锚下挤，这次连生长方向一起定死）。
 *                     {@link #AUTO_ANCHOR} = 自动：贴着 HUD 带上方（见 {@link #anchorTop}）。
 * @param swayEnabled  摇摆呼吸开关：停留期间整卡轻微摇摆的「活着」感。默认关 —— 0.2.3 的
 *                     新档位只加选项不改默认，老玩家的观感不因更新而变（有测试钉死）。
 *
 * <p>【为什么锚点是分数而不是像素】画布宽随 GUI 缩放剧烈变化（1280×720 上 guiScale 3 是 426 宽、
 * guiScale 5 只剩 256 宽）。写死绝对坐标的话，换一档缩放锚点就被边界夹住 ——
 * "设了等于没设的值比没有这个值更坏"（本仓库记过的老坑）。分数在所有缩放档下都落在同一个相对位置，
 * 而且配置界面里的拖拽编辑天然算出来的就是分数。
 *
 * <p>【2026-09-19 为什么自动档从"准星下方"回到"右下贴底"】准星（50% 高）往下要给整个底部
 * HUD 带让位 75px，常见画布（427×240）锚点以下只剩 33px —— 5 张卡需要 116px，自动缩放
 * 从此永远激活且按张数一档一档跳（100%→75%→60%），放不下还硬切摘卡。这不是调参能救的，
 * 是锚点选址与"HUD 带上方"的几何冲突（第四批反馈审计定案：数量优先）。右下贴底后
 * "锚点以上的整段屏幕"都是卡堆的地盘，常见画布真能放满同屏上限。
 *
 * <p>【2026-09-18 删掉的两组键】{@code stickTo}（贴左/贴右）与 {@code leftEdge}（绝对像素）——
 * 它们管的事现在全部由 {@code anchorX}/{@code anchorY} 表达。
 */
public record LayoutSettings(Appear appearMode, Exit exitMode, Side align, float separation,
                             int scalePercent, float anchorX, float anchorY, boolean mirrorCard,
                             boolean swayEnabled) {

    /**
     * 八参快捷构造：sway 缺省关。canonical 尾上加组件后把旧签名留成 overload，
     * 现有调用（配置采样、测试）一个字不用改 —— 「现有调用零破坏」就靠它。
     */
    public LayoutSettings(Appear appearMode, Exit exitMode, Side align, float separation,
                          int scalePercent, float anchorX, float anchorY, boolean mirrorCard) {
        this(appearMode, exitMode, align, separation, scalePercent, anchorX, anchorY, mirrorCard, false);
    }

    /**
     * Boolean 桥接：TOML 解析层缺键拿到 null 时走这里。null 一律视为关 ——
     * 宁可少一档效果，也不把"没填"猜成"要开"。
     */
    public LayoutSettings(Appear appearMode, Exit exitMode, Side align, float separation,
                          int scalePercent, float anchorX, float anchorY, boolean mirrorCard,
                          Boolean swayEnabled) {
        this(appearMode, exitMode, align, separation, scalePercent, anchorX, anchorY, mirrorCard,
                Boolean.TRUE.equals(swayEnabled));
    }

    /** 七参快捷构造（不镜像）：镜像默认关，测试与"只关心水平语义"的调用少写一个 false。 */
    public LayoutSettings(Appear appearMode, Exit exitMode, Side align, float separation,
                          int scalePercent, float anchorX, float anchorY) {
        this(appearMode, exitMode, align, separation, scalePercent, anchorX, anchorY, false);
    }

    /** 卡片间距的默认值（像素）。 */
    public static final float DEFAULT_SEPARATION = 4f;

    /** {@code scalePercent} = 自动：按"这一摞卡塞不塞得进可用高度"决定缩放。 */
    public static final int AUTO_SCALE = 0;
    /** 手动缩放的上下限（百分比）。 */
    public static final int MIN_SCALE_PERCENT = 50;
    public static final int MAX_SCALE_PERCENT = 200;
    /**
     * 自动缩放的<b>下限</b>：再小就看不清名字了 —— 到了这一步应该少显示几张卡
     * （调用方接排队，不再硬切），而不是把卡缩成一条缝。
     */
    public static final int MIN_AUTO_PERCENT = 60;

    /** 锚点的"自动"哨兵：anchorX 按对齐档算，anchorY 贴着 HUD 带上方。 */
    public static final float AUTO_ANCHOR = -1f;

    /** 卡片出现时的展开方式。 */
    public enum Appear {
        /** 内容保持原样，从竖条后面平移到最终位置（界面上的「火车」档）。 */
        SLIDE,
        /** 内容位置不动，可见范围从左往右慢慢展开（界面上的「拉幕」档）。 */
        CLIP,
        /** 滑出 + 过冲回弹：与火车同路线，但冲过终点一截再被拉回来（easeOutBack 的过冲）。 */
        BOUNCE,
        /** 掉落：沿火车路线斜着滑进来（水平走火车那趟），同时从锚线上方轻微下落，落地带一记小弹。 */
        DROP
    }

    /** 卡片的消失方式：和入场对称的那一半。各档都叠加透明度下降，不会硬切。 */
    public enum Exit {
        /** 原地淡出。 */
        FADE,
        /** 内容整块平移回竖条后面 —— 火车入场的逆放。 */
        TRAIN,
        /** 可见范围从右往左收拢 —— 拉幕入场的逆放。 */
        WIPE,
        /** 下坠：向下加速坠落 + 淡出。 */
        FALL,
        /** 缩放消失：整卡绕卡心等比缩小 + 淡出（不位移、不单轴收窄）。 */
        SCALE
    }

    /** 水平对齐基准：锚线管的是卡的哪一条边。 */
    public enum Side {
        /** 竖条左缘贴锚线：一摞卡的竖条成一条竖线。 */
        LEFT,
        /** 卡片右缘贴锚线：右缘齐、左缘随卡宽参差（设计里的「右边缘对齐」；出厂默认）。 */
        RIGHT
    }

    /** 默认情况下卡片离屏幕左/右边的距离（自动锚点用它推"最宽卡右缘贴右边距"）。 */
    private static final int MARGIN_X = 16;

    /**
     * 卡片内容允许占屏宽的比例 —— 卡宽上限（{@code CardMetrics.maxWidth}）与自动锚点
     * 要预留多少右边空间共用它。两个地方各写一份的话，调了一处另一处就不对。
     */
    public static final float CONTENT_WIDTH_RATIO = 0.45f;

    /**
     * 左缘档的自动锚线（老公式）：让<b>最宽的那张卡</b>右缘正好落在右边距上，竖条成一条线。
     */
    public static float autoLeftEdge(float guiWidth) {
        float budget = guiWidth * CONTENT_WIDTH_RATIO;
        return Math.max(0f, guiWidth - MARGIN_X - budget);
    }

    /** 默认：火车入场、<b>火车退回</b>（与入场对称的退场，2026-09-19 用户定案）、右缘对齐
     *  （2026-09-20 用户定案：卡宽随名字参差时，齐的该是靠屏幕边的那一侧）、锚点自动
     *  （右下贴 HUD 带）、卡片不镜像、摇摆呼吸关（0.2.3 定案：新档只加选项，默认观感不变）。 */
    public static LayoutSettings defaults() {
        return new LayoutSettings(Appear.SLIDE, Exit.TRAIN, Side.RIGHT, DEFAULT_SEPARATION,
                AUTO_SCALE, AUTO_ANCHOR, AUTO_ANCHOR, false, false);
    }

    /**
     * 这一帧该用多大的缩放（1.0 = 100%）。
     * <p>【自动档怎么算】需要的高度 = 张数 × 卡高 + 间距（全按 100% 算），可用高度放不下时按比例缩，
     * 下限 {@link #MIN_AUTO_PERCENT}%；装得下就恒为 100% —— <b>空着的屏幕不该把卡撑大</b>。
     * <p>【available 是锚点<b>以上</b>那段】卡堆向上生长，地盘是锚线到屏幕顶。
     */
    public float scale(float available, float cardHeight, int cards, float gap) {
        if (scalePercent > 0) {
            return scalePercent / 100f;
        }
        if (cards <= 0 || cardHeight <= 0f || available <= 0f) {
            return 1f;
        }
        float needed = cards * cardHeight + gap * Math.max(0, cards - 1);
        if (needed <= available) {
            return 1f;
        }
        float ratio = available / needed;
        return Math.max(MIN_AUTO_PERCENT / 100f, Math.min(1f, ratio));
    }

    /** 外部来的值一律过一遍：配置文件是玩家可改的，非法值不该变成崩溃或卡片消失。 */
    public LayoutSettings sanitized() {
        return new LayoutSettings(
                appearMode == null ? Appear.SLIDE : appearMode,
                exitMode == null ? Exit.FADE : exitMode,
                align == null ? Side.RIGHT : align,
                Math.max(0f, Math.min(32f, separation)),
                // 0 = 自动；给了数值就夹进 50..200 —— 300% 会把卡顶出屏幕，10% 没人看得见
                scalePercent == AUTO_SCALE ? AUTO_SCALE
                        : Math.max(MIN_SCALE_PERCENT, Math.min(MAX_SCALE_PERCENT, scalePercent)),
                sanitizeAnchor(anchorX),
                sanitizeAnchor(anchorY),
                // 布尔组件没有 null 可防，原样放行；新枚举值走上面的 == null 分支自然原样通过
                mirrorCard,
                swayEnabled);
    }

    /** 锚点只许两种值：自动哨兵，或者 0..1 的比例。别的统统回自动 —— 宁可回默认也不猜。 */
    private static float sanitizeAnchor(float v) {
        if (v == AUTO_ANCHOR) {
            return AUTO_ANCHOR;
        }
        return (v < 0f || v > 1f) ? AUTO_ANCHOR : v;
    }

    /**
     * 这一帧锚线的横坐标（屏幕逻辑 px）。
     * 左缘锚定时＝竖条左缘该在的位置；右缘对齐时＝卡片右缘该在的位置。
     * <p>【自动档按对齐档各自解析】左缘档用 {@link #autoLeftEdge}（竖条成线那条老公式）；
     * 右缘档直接贴右边距 —— 从前右缘档错拿左缘公式的数当右缘，卡会瞬移到屏幕中左
     * （第四批反馈"锚定错乱"的三处之一）。手动分数两种档同值不同义，这是配置写明的。
     */
    public float anchorLeft(float guiWidth) {
        if (anchorX >= 0f) {
            return anchorX * guiWidth;
        }
        return align == Side.RIGHT ? guiWidth - MARGIN_X : autoLeftEdge(guiWidth);
    }

    /**
     * 这一帧锚点的纵坐标（屏幕逻辑 px）＝ 第一张卡（最新）顶边的位置，卡堆从这里<b>向上</b>长。
     * <p>【自动档 = 贴着 HUD 带上方】最新那张的顶边正好落在底部留白之上 —— 新卡出现的地点
     * 固定，且离拾取发生的快捷栏最近；常见画布上"同屏上限"不再被几何砍半。
     * <p>【自定义档 = 拖到哪儿就是哪儿（2026-09-19 深夜改）】从前这里还夹了一道"至少放得下
     * 一张"，编辑场底部一整条拖进去没反应 —— 用户原话"不能全屏幕拖，有限制"。夹取删了：
     * 锚点是玩家的明确选择，压到 HUD 带上/拖出屏底都照算；放得下几张由几何容量自己少排
     * （{@code StackLayout#fittingCount} + 账本容量门），多的去排队。
     */
    public float anchorTop(float guiHeight, float cardHeight, int bottomMargin) {
        return anchorY < 0f ? guiHeight - bottomMargin - cardHeight : anchorY * guiHeight;
    }
}
