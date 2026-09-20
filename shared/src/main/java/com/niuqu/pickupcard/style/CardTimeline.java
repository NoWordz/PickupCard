package com.niuqu.pickupcard.style;

/**
 * 一张卡的动画时间轴：只回答"现在 t 是多少"，不碰颜色、不碰渲染。
 * <p>
 * 【为什么是纯函数】时间轴的三段（入场/跳动/退场）全部由调用方传时刻进来，
 * 单测不需要伪造任何时钟。"合并把一张正在退场的卡拉回来"这类边界由渲染层改状态，
 * 本类只负责算。
 *
 * @param enterMs      入场时长（0 = 没有入场，t 恒为 1）
 * @param bumpMs       数字跳动时长
 * @param enterEnabled 入场动画开关（逐动画可关，动画敏感玩家友好）
 * @param bumpEnabled  跳动开关
 */
public record CardTimeline(long enterMs, long bumpMs, boolean enterEnabled, boolean bumpEnabled) {

    public static CardTimeline defaults() {
        return new CardTimeline(480L, 300L, true, true);
    }

    /**
     * 「淡回」时长的兜底值：主题没给 {@code --pc-revive-ms} 时用它（正常路径见
     * {@link com.niuqu.pickupcard.style.StyleModel#reviveMs()}）。
     * <p>
     * 【为什么不是瞬间回到 1】从前那一版就是瞬间的 —— 屏幕上看是「淡到一半突然全不透明」，
     * 也就是用户 2026-09-17 报的那个 bug。
     * <p>
     * 【为什么从 160 挪到 300】160ms 那个数是在"淡回只跟入场比"的前提下定的，漏了真正对称的
     * 那一头：<b>淡出</b>。淡出 480ms、淡回 160ms = 回来比离开快 3 倍，而救回往往发生在淡出
     * 末尾（卡片已经淡到 alpha ≈ 0.01）—— 于是"文字和图标突然闪一下"（用户 2026-09-18 的原话）。
     * 300ms 比离开利落、又不至于快到读成闪；它在主题里，跟别的动画参数一样可调。
     */
    public static final long DEFAULT_REVIVE_MS = 300L;

    /**
     * 入场两段的窗口 —— 2026-09-17 定的节奏，配 {@code --pc-enter-ms: 480}：
     * <pre>
     *   0 ──── 240ms ───── 403ms ──── 480ms
     *   │ 竖条长满 │ 内容走出隧道口 │ 静止 77ms
     *   0        50%             84%      100%
     * </pre>
     * 【为什么是这三个数】用户原话"目前太慢了。调快点，处理好竖条和卡片的关系"。
     * 800ms 的毛病出在后半段：内容要走完一整张卡的宽度，却给了它 656ms 平推，看着像慢慢蹭
     * 出来。现在竖条占前一半（240ms）就长满，内容 96ms 起跑、403ms 到位，尾巴留 77ms 静止
     * —— <b>两段仍然重叠</b>（不串行），所以"先开洞、东西再从洞里抽出来"的味道留着，
     * 只是快了近一倍。
     */
    private static final float BAR_END = 0.50f;
    /** 内容从 20%（96ms）起跑：比竖条长完早，两条动画叠着走，不脱节。 */
    private static final float CONTENT_START = 0.20f;
    /** 内容在 84%（403ms）到位；剩下的 77ms 什么都不动，让"到位"这一下能被看见。 */
    private static final float CONTENT_END = 0.84f;

    /**
     * 竖条展开的缓动 = {@code cubic-bezier(.2,.9,.3,1)}（参考设计里展开那档就用它）。
     * <p>
     * 【为什么必须由本类兑现，而不是"留给渲染层顺手 easing 一下"】这正是它丢过一次的原因：
     * 本类原来把 {@code enter()} 归一化完就交出去，注释写着"easing 在渲染层做"——
     * 而渲染层<b>没有做</b>，于是竖条和内容都是匀速动，玩家看到的评价是"很僵硬，没有曲线"。
     * 一条只写在注释里的约定，等于没有。
     */
    private static final CubicBezier BAR_CURVE = CubicBezier.BAR;

    /**
     * 内容滑出的缓动 = {@link CubicBezier#CONTENT}（Material 标准曲线）。
     * <p>
     * 参考设计原来那条是 {@code cubic-bezier(.22,.9,.28,1)}，前段陡到 t=0.25 就走 0.757 ——
     * 用户真机上的评价是"冲得太快/持续时间太短"。换这条之后 t=0.25 只走 0.237。
     * **当时设计侧的曲线也同步换成了同一条**，两边不允许不一致。
     */
    private static final CubicBezier CONTENT_CURVE = CubicBezier.CONTENT;

    /**
     * 入场进度 ∈ [0,1]，<b>是归一化的钟，不是曲线</b>。未启用时恒为 1（直接出现在终点）。
     * 曲线各自加在 {@link #bar} 与 {@link #content} 上：两段的起跑时刻不同，共用一个
     * 已经加过缓动的 t 会把"先开竖条、再出内容"的错峰压掉。
     */
    public float enter(long now, long bornAt) {
        if (!enterEnabled || enterMs <= 0L) return 1f;
        return Easing.clamp01((now - bornAt) / (float) enterMs);
    }

    /**
     * 竖条自身的展开进度 ∈ [0,1]：在入场的头 50%（240ms）里从 0 长到满。
     * <p>
     * 【为什么竖条要单独有一条进度】"竖条先开、内容再出"是两段式，不是一条进度曲线能
     * 表达的。合成一条就会出现"竖条还没长完、内容已经开始挤出来"，看起来像卡在了半路。
     */
    public float bar(long now, long bornAt) {
        return BAR_CURVE.at(window(enter(now, bornAt), 0f, BAR_END));
    }

    /**
     * 内容滑出的进度 ∈ [0,1]：从 20% 起跑，84% 跑完（见上面的窗口注释）。
     * 入场位移与缩放也用它——两处必须同源，否则外壳和文字会错位。
     */
    public float content(long now, long bornAt) {
        return CONTENT_CURVE.at(window(enter(now, bornAt), CONTENT_START, CONTENT_END));
    }

    /** 把 [0,1] 的总进度映射到子区间 [a,b] 上的 [0,1]。 */
    private static float window(float t, float a, float b) {
        if (b <= a) return 1f;
        return Easing.clamp01((t - a) / (b - a));
    }

    /** 数字跳动进度 ∈ [0,1]。未启用或从未跳动时恒为 1（无缩放）。 */
    public float bump(long now, long bumpAt) {
        if (!bumpEnabled || bumpMs <= 0L || bumpAt < 0L) return 1f;
        return Easing.clamp01((now - bumpAt) / (float) bumpMs);
    }

    /** 退场进度 ∈ [0,1]。退场由渲染层记 startAt，本类只按 {@code exitMs} 归一化。 */
    public static float exit(long now, long exitStartAt, long exitMs) {
        if (exitMs <= 0L) return 1f;
        return Easing.clamp01((now - exitStartAt) / (float) exitMs);
    }

    /**
     * 退场中的不透明度 ∈ [0,1]：{@code 1 - easeOutCubic(进度)}。
     * <p>
     * 【为什么单开一个纯函数】这条曲线有两个用它的地方：画的时候（要按它设色）和
     * <b>决定要不要救回的时候</b>（淡到几乎看不见的卡不该被拽回来，见 {@code CardStage}）。
     * 两处各写一遍迟早会分叉，而分叉的表现正是"判据说还看得见、画出来却已经没了"。
     */
    public static float exitAlpha(long now, long exitStartAt, long exitMs) {
        return 1f - Easing.easeOutCubic(exit(now, exitStartAt, exitMs));
    }
}
