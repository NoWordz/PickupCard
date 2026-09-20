package com.niuqu.pickupcard.style;

/**
 * CSS 的 {@code cubic-bezier(x1, y1, x2, y2)}。
 *
 * <p>【为什么需要它】界面里的过渡全是这一族曲线（340ms 那条写的就是
 * cubic-bezier(.22,.9,.28,1)），而 Java 这边原先只有手挑的 easeOutBack / easeOutCubic。
 * 想照曲线还原就得能表达那一条，而不是找个近似的替身：
 * 这条曲线在 t=0.25 时已经走了 0.757，而 easeOutCubic 只走 0.578 —— 前段差了近两成，
 * 眼睛看得出来（"一下就滑到位" vs "慢慢挪过去"）。用近似值就等于把还原打了折。
 *
 * <p>【怎么解】x(u) 是三次多项式，给定 x 反解 u 没有解析解。用牛顿迭代（导数同样是多项式，
 * 收敛快），万一不收敛退到二分 —— 二分慢但一定收敛，两者结合不需要任何魔数。
 */
public final class CubicBezier {

    /** 卡片被推到新位置的过渡：340ms。 */
    public static final CubicBezier SLOT = new CubicBezier(0.22f, 0.9f, 0.28f, 1f);
    /** 竖条展开：180ms。 */
    public static final CubicBezier BAR = new CubicBezier(0.2f, 0.9f, 0.3f, 1f);
    /** CSS 的 ease，退场淡出用。 */
    public static final CubicBezier EASE = new CubicBezier(0.25f, 0.1f, 0.25f, 1f);

    /**
     * 内容滑出的曲线（Material 标准曲线）。
     * <p>
     * 【为什么不用参考设计原来那条 {@link #SLOT}】2026-09-17 用户真机反馈："持续时间太短，
     * 冲得太快"。{@code SLOT} 前段极陡（t=0.25 已经走了 0.757），560ms 的入场里内容
     * 386ms 就到位了 —— 那正是"冲出来"的观感来源。这条在 t=0.25 只走 0.237，出洞的过程
     * 才看得见。
     * <p>
     * **这是一次故意的偏离**：偏离可以，但两处必须一起改 —— 否则下次拿设计对游戏，对出来
     * 的是个假差异（当初这条曲线就是设计侧与这里同步换掉的）。
     * 卡片换位那条（{@code CardMove}）仍走 {@link #SLOT}，没动。
     */
    public static final CubicBezier CONTENT = new CubicBezier(0.4f, 0f, 0.2f, 1f);

    private static final int NEWTON_STEPS = 8;
    private static final int BISECTION_STEPS = 40;
    private static final float EPSILON = 1e-5f;

    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;

    public CubicBezier(float x1, float y1, float x2, float y2) {
        // x 控制点必须落在 [0,1]：越界会让曲线回头，x(t) 不再单调，反解就有多解
        this.x1 = clampUnit(x1);
        this.x2 = clampUnit(x2);
        this.y1 = y1;
        this.y2 = y2;
    }

    /**
     * @param t 进度 ∈ [0,1]
     * @return 缓动后的值；y 控制点超出 [0,1] 时会过冲（参考设计里一条都没有这种）
     */
    public float at(float t) {
        t = Easing.clamp01(t);
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return bezier(solve(t), y1, y2);
    }

    /** y 控制点是否越界（= 会过冲）。{@code easeOutBack} 那种回弹就是这一类。 */
    public boolean overshoots() {
        return y1 < 0f || y1 > 1f || y2 < 0f || y2 > 1f;
    }

    /** 给定 x 反解参数 u。 */
    private float solve(float x) {
        float u = x;
        for (int i = 0; i < NEWTON_STEPS; i++) {
            float error = bezier(u, x1, x2) - x;
            if (Math.abs(error) < EPSILON) {
                return u;
            }
            float d = slope(u, x1, x2);
            if (Math.abs(d) < 1e-6f) {
                break;
            }
            u -= error / d;
            if (u < 0f || u > 1f) {
                break;
            }
        }
        float lo = 0f;
        float hi = 1f;
        u = x;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            float value = bezier(u, x1, x2);
            if (Math.abs(value - x) < EPSILON) {
                return u;
            }
            if (value < x) {
                lo = u;
            } else {
                hi = u;
            }
            u = (lo + hi) / 2f;
        }
        return u;
    }

    private static float bezier(float u, float c1, float c2) {
        float v = 1f - u;
        return 3f * v * v * u * c1 + 3f * v * u * u * c2 + u * u * u;
    }

    /** 三次多项式对 u 的导数。 */
    private static float slope(float u, float c1, float c2) {
        float v = 1f - u;
        return 3f * v * v * c1 + 6f * v * u * (c2 - c1) + 3f * u * u * (1f - c2);
    }

    private static float clampUnit(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
