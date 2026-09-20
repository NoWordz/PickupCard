package com.niuqu.pickupcard.style;

/**
 * 动画缓动曲线库。<b>曲线形状的正本就在这里</b>（{@link CubicBezier} / {@link CardTimeline}
 * 里那几条贝塞尔同理）。
 * <p>
 * 时长不在这里：入场 / 跳动 / 淡回三条时长定义在 {@code design/tokens.css}
 * （{@code --pc-enter-ms} / {@code --pc-bump-ms} / {@code --pc-revive-ms}），由
 * {@code tools/css_tokens.py} 编译进 {@code assets/pickupcard/styles/*.json} 后供 Java 读取；
 * 退场时长走 TOML（见 {@link com.niuqu.pickupcard.layout.LayoutSettings}）。
 * 改曲线只改这里，改完跑 {@code CardTimelineTest} / {@code CubicBezierTest} —— 曲线形状被已知答案钉着。
 */
public final class Easing {

    private Easing() {
    }

    /** 线性。 */
    public static float linear(float t) {
        return clamp01(t);
    }

    /** easeOutCubic：快出慢停，透明度的默认选择。 */
    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /**
     * easeOutBack：带过冲的回弹。峰值在 t≈0.7 处约 1.1 倍——"弹性"档的入场/数字跳动
     * 用的就是它。overshoot &gt; 1 是本函数的特性不是 bug，调用方拿它做位移/缩放时要接受
     * 短暂超出目标值。
     */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** easeInQuad：慢起快收，退场位移用。 */
    public static float easeInQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    /**
     * 单峰脉冲：t=0 与 t=1 时为 1，t=0.5 处到达峰值 {@code peak}。
     * 数字合并跳动就是它乘上幅度——起点结束都在 1 倍，中间鼓一下。
     */
    public static float pulse(float t, float peak) {
        t = clamp01(t);
        return 1f + (peak - 1f) * (float) Math.sin(Math.PI * t);
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : Math.min(1f, t);
    }
}
