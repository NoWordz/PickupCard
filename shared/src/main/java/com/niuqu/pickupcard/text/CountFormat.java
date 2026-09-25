package com.niuqu.pickupcard.text;

/**
 * 数量怎么写成字。
 * <p>
 * 【为什么只有一个方法】上一版把"加正号"和"数量格式"拆在两个地方：countFormat 负责
 * {@code ×64}，调用方再拼 {@code "+"}，于是屏幕上出现了 {@code +×1} —— 两个前缀叠在一起。
 * 病根不是算错，是【同一件事有两个负责人】。这里把整串显示文本收进一个方法，
 * 调用方不许再拼任何字符，这类 bug 从结构上就不成立。
 */
public enum CountFormat {

    /** +64 —— 默认。一眼看出"这次进账多少"。 */
    PLUS,
    /** ×64 —— 老式堆叠写法。 */
    X_PREFIX,
    /** 64 —— 只写数字，最安静。 */
    PLAIN,
    /** +1.2K —— 大数量下不占宽度。 */
    ABBREVIATED;

    /**
     * 把"这一次捡到几个"写成完整显示文本，含符号。
     *
     * @param count 本次进账数量
     * @return 直接可画的字符串；调用方不要再前置任何符号
     */
    public String gain(int count) {
        return switch (this) {
            case PLUS -> "+" + group(count);
            case X_PREFIX -> "×" + group(count);
            case PLAIN -> group(count);
            case ABBREVIATED -> "+" + abbreviate(count);
        };
    }

    /**
     * 把"背包里现在有几个"写成完整显示文本 —— <b>不带进账符号</b>：数量口径是
     * 持有量而不是进账量，{@code +64} 的那个 {@code +} 在这里就是错话。
     * 缩写档与 {@link #gain} 同一把尺（千位起缩、一位小数封顶），只是不前置 {@code +}。
     *
     * @param count 物品栏持有总数
     * @return 直接可画的字符串；调用方不要再前置任何符号
     */
    public String hold(int count) {
        return this == ABBREVIATED ? abbreviate(count) : group(count);
    }

    /** 不加符号的数字本身（给需要自己控制布局的场合用）。 */
    public String group(int count) {
        return Integer.toString(count);
    }

    private static String abbreviate(int value) {
        int abs = Math.abs(value);
        if (abs >= 1_000_000_000) return trim(value / 1_000_000_000.0) + "B";
        if (abs >= 1_000_000) return trim(value / 1_000_000.0) + "M";
        if (abs >= 1_000) return trim(value / 1_000.0) + "K";
        return Integer.toString(value);
    }

    /** 1.24K 这种精度对"捡了多少个"没意义，一位小数封顶。 */
    private static String trim(double value) {
        if (value >= 100) return String.format("%.0f", value);
        return String.format("%.1f", value);
    }
}
