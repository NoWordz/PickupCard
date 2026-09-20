package com.niuqu.pickupcard.style;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * 玩家在配置界面里改的"外观参数"。
 *
 * <p>【它和主题 JSON 是什么关系】主题（{@code assets/pickupcard/styles/*.json}）是**一套设计**，
 * 配置是**玩家在这套设计上的个人改动**。<b>主题给默认值，配置只覆盖它改过的项</b> ——
 * 所以每个字段都是可选的：没设过 = "这项没动，用主题的"。这样"换主题"和
 * "我只把圆角调大一点"两件事不会互相覆盖。
 *
 * <p>【为什么用 builder 而不是 15 个位置参数】这一排参数里有一半是同类型的 Optional，
 * 写错位置编译器不会报错、单测也很难发现（表现是"改阴影结果圆角变了"）。
 *
 * <p>【一个键只有一个家】配置界面写的就是这份值（落盘在 TOML 的 {@code [style]} 段），
 * 界面不另存一份。主题 JSON 里同名键仍然有效，只是会被这里压过去。
 */
public record StyleOverrides(OptionalInt cornerRadius,
                             OptionalInt paddingH,
                             OptionalInt paddingV,
                             OptionalInt gap,
                             OptionalInt iconSize,
                             OptionalInt barWidth,
                             OptionalInt barInsetY,
                             OptionalInt borderWidth,
                             Optional<Integer> fillTop,
                             Optional<Integer> fillBottom,
                             Optional<Integer> border,
                             Optional<Integer> nameColor,
                             OptionalLong enterMs,
                             OptionalLong bumpMs,
                             OptionalLong reviveMs,
                             OptionalInt bumpPeakPercent,
                             Optional<Boolean> enterEnabled,
                             Optional<Boolean> bumpEnabled) {

    /**
     * 解析玩家手打的颜色：{@code #AARRGGBB} / {@code #RRGGBB}（补 FF）/ {@code AARRGGBB}。
     * <p>
     * 【为什么放在这儿、而不是配置层】颜色是主题的一部分，读法就该和主题同一个地方；
     * 而且它是纯函数，可以离线单测 —— 配置层的解析坏掉只会在玩家打字那一刻才现形。
     *
     * @return 解析不了就是空（调用方当作"这项没设过"，不覆盖主题）
     */
    public static Optional<Integer> parseArgb(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                value |= 0xFF000000L;      // 没写 alpha 就是不透明
            }
            return Optional.of((int) value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 一项都没覆盖 = 完全用主题。 */
    public static StyleOverrides none() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 把覆盖盖到主题上。
     * <p>
     * 【顺序不能反】先覆盖、再 {@link StyleModel#sanitized()} —— 玩家可以在 TOML 里手写任何
     * 数字，夹逼必须是最后一道。反过来等于给玩家开了一个能把渲染打崩的口子。
     */
    public StyleModel apply(StyleModel theme) {
        return new StyleModel(
                cornerRadius.orElse(theme.cornerRadius()),
                paddingH.orElse(theme.paddingH()),
                paddingV.orElse(theme.paddingV()),
                gap.orElse(theme.gap()),
                iconSize.orElse(theme.iconSize()),
                barWidth.orElse(theme.barWidth()),
                barInsetY.orElse(theme.barInsetY()),
                borderWidth.orElse(theme.borderWidth()),
                fillTop.orElse(theme.fillTop()),
                fillBottom.orElse(theme.fillBottom()),
                border.orElse(theme.border()),
                theme.glowAlpha(),
                theme.shimmerAlpha(),
                nameColor.orElse(theme.nameColor()),
                enterMs.orElse(theme.enterMs()),
                bumpMs.orElse(theme.bumpMs()),
                reviveMs.orElse(theme.reviveMs()),
                bumpPeakPercent.orElse(theme.bumpPeakPercent()),
                enterEnabled.orElse(theme.enterEnabled()),
                bumpEnabled.orElse(theme.bumpEnabled()),
                theme.glowPulseEnabled(),
                // 强调色不在 TOML 覆盖项里：它是"哪一档"的色表，整套归主题（或 RarityCore）
                theme.accents()).sanitized();
    }

    /** 可变构建器：每设一项都显式命名，杜绝位置错。 */
    public static final class Builder {
        private OptionalInt cornerRadius = OptionalInt.empty();
        private OptionalInt paddingH = OptionalInt.empty();
        private OptionalInt paddingV = OptionalInt.empty();
        private OptionalInt gap = OptionalInt.empty();
        private OptionalInt iconSize = OptionalInt.empty();
        private OptionalInt barWidth = OptionalInt.empty();
        private OptionalInt barInsetY = OptionalInt.empty();
        private OptionalInt borderWidth = OptionalInt.empty();
        private Optional<Integer> fillTop = Optional.empty();
        private Optional<Integer> fillBottom = Optional.empty();
        private Optional<Integer> border = Optional.empty();
        private Optional<Integer> nameColor = Optional.empty();
        private OptionalLong enterMs = OptionalLong.empty();
        private OptionalLong bumpMs = OptionalLong.empty();
        private OptionalLong reviveMs = OptionalLong.empty();
        private OptionalInt bumpPeakPercent = OptionalInt.empty();
        private Optional<Boolean> enterEnabled = Optional.empty();
        private Optional<Boolean> bumpEnabled = Optional.empty();

        public Builder cornerRadius(int value) {
            cornerRadius = OptionalInt.of(value);
            return this;
        }

        public Builder paddingH(int value) {
            paddingH = OptionalInt.of(value);
            return this;
        }

        public Builder paddingV(int value) {
            paddingV = OptionalInt.of(value);
            return this;
        }

        public Builder gap(int value) {
            gap = OptionalInt.of(value);
            return this;
        }

        public Builder iconSize(int value) {
            iconSize = OptionalInt.of(value);
            return this;
        }

        public Builder barWidth(int value) {
            barWidth = OptionalInt.of(value);
            return this;
        }

        public Builder barInsetY(int value) {
            barInsetY = OptionalInt.of(value);
            return this;
        }

        public Builder borderWidth(int value) {
            borderWidth = OptionalInt.of(value);
            return this;
        }

        public Builder fillTop(int argb) {
            fillTop = Optional.of(argb);
            return this;
        }

        public Builder fillBottom(int argb) {
            fillBottom = Optional.of(argb);
            return this;
        }

        public Builder border(int argb) {
            border = Optional.of(argb);
            return this;
        }

        public Builder nameColor(int argb) {
            nameColor = Optional.of(argb);
            return this;
        }

        public Builder enterMs(long value) {
            enterMs = OptionalLong.of(value);
            return this;
        }

        public Builder bumpMs(long value) {
            bumpMs = OptionalLong.of(value);
            return this;
        }

        public Builder reviveMs(long value) {
            reviveMs = OptionalLong.of(value);
            return this;
        }

        public Builder bumpPeakPercent(int value) {
            bumpPeakPercent = OptionalInt.of(value);
            return this;
        }

        public Builder enterEnabled(boolean value) {
            enterEnabled = Optional.of(value);
            return this;
        }

        public Builder bumpEnabled(boolean value) {
            bumpEnabled = Optional.of(value);
            return this;
        }

        public StyleOverrides build() {
            return new StyleOverrides(cornerRadius, paddingH, paddingV, gap, iconSize, barWidth,
                    barInsetY, borderWidth, fillTop, fillBottom, border, nameColor,
                    enterMs, bumpMs, reviveMs, bumpPeakPercent, enterEnabled, bumpEnabled);
        }
    }
}
