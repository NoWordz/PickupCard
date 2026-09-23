package com.niuqu.pickupcard.config;

import dev.e33.trellis.ui.widget.Widget;
import dev.e33.trellis.ui.widget.PaintCtx;
import dev.e33.trellis.geom.Rect;

/**
 * 测试用的两种控件替身。
 *
 * <p>【为什么要替身】A-10 之后"行形态"是用控件数组表达（{@code null} = 小节头），
 * 而几何对账（{@link TrellisHitParityTest} / {@link TrellisLabelFitTest}）只关心
 * <b>哪几行有控件</b>，不关心控件怎么画；输入路由（{@link TrellisControlInputTest}）
 * 则要一个"记得住自己收到了什么"的控件。<b>不要</b>拿真控件来测 ——
 * 真控件会去碰 Forge 配置，而这几条必须在没启动游戏时也能跑。
 */
final class TestWidgets {

    private TestWidgets() {
        throw new AssertionError("no instances");
    }

    /** 只占一格：不画、不反应。 */
    static final class Inert extends Widget {
        Inert() {
            super("inert");
        }

        @Override
        protected void paint(PaintCtx ctx) {
        }
    }

    /**
     * 把"收到过什么"<b>全部</b>记下来（A-23 组件边界的离线网）。
     *
     * <p>【为什么要一个记这么全的替身】{@code WidgetSlot} 那道桥今天<b>零离线覆盖</b> ——
     * 焦点环、按下/抬起、键转交、绘制盒子的身份，全都只在真机上验过。搬它之前先有一张网，
     * 否则搬坏了只有肉眼能发现（坑 8：编辑场两颗按钮"有焦点环、点不动"，而三轮真机全绿）。
     *
     * <p>{@link #paintedBox} 存的是<b>对象</b>，断言时用 {@code assertSame} —— 判据 1 要的是
     * "同一个 {@code Rect}"，不是"两个相等的"。
     */
    static final class Probe extends Widget {
        Rect paintedBox;
        long paintedNow = -1L;
        int paints;

        Rect pressBox;
        double pressX;
        double pressY;
        int presses;
        int releases;
        int activates;
        boolean lastActivate;

        int keyCode = Integer.MIN_VALUE;
        int keyMods;
        int keys;
        /** 最近一次收到的"是不是长按重复"（A-33）。 */
        boolean lastRepeat;
        char typed;

        boolean hasFocus;
        int focusChanges;

        Probe() {
            super("probe");
        }

        /** 这一格现在"正被按着"吗（基类那个字段是 protected，这里转出来给断言用）。 */
        boolean held() {
            return pressed;
        }

        @Override
        public boolean press(Rect box, double mouseX, double mouseY, int button) {
            pressBox = box;
            pressX = mouseX;
            pressY = mouseY;
            boolean taken = super.press(box, mouseX, mouseY, button);
            if (taken) {
                presses++;
            }
            return taken;
        }

        @Override
        public void release(Rect box, double mouseX, double mouseY, boolean activate) {
            releases++;
            lastActivate = activate;
            super.release(box, mouseX, mouseY, activate);
        }

        @Override
        protected void onActivate() {
            activates++;
        }

        @Override
        public boolean keyPressed(int code, int modifiers, boolean repeat) {
            keyCode = code;
            keyMods = modifiers;
            lastRepeat = repeat;
            keys++;
            return true;
        }

        @Override
        public boolean charTyped(char c) {
            typed = c;
            return true;
        }

        @Override
        protected void onFocusChanged(boolean value) {
            hasFocus = value;
            focusChanges++;
        }

        @Override
        protected void paint(PaintCtx ctx) {
            paintedBox = ctx.box();
            paintedNow = ctx.now();
            paints++;
        }
    }

    /** 记下收到的输入（按下 / 松开 / 激活 / 悬停）。 */
    static final class Recorder extends Widget {
        int presses;
        int releases;
        int activates;
        int hoverChanges;
        boolean lastHovered;
        boolean lastActivate;

        Recorder() {
            super("recorder");
        }

        /** 这一格现在"正被按着"吗（基类那个字段是 protected，这里转出来给断言用）。 */
        boolean held() {
            return pressed;
        }

        @Override
        public boolean press(Rect box, double mouseX, double mouseY, int button) {
            boolean taken = super.press(box, mouseX, mouseY, button);
            if (taken) {
                presses++;
            }
            return taken;
        }

        @Override
        public void release(Rect box, double mouseX, double mouseY, boolean activate) {
            releases++;
            lastActivate = activate;
            super.release(box, mouseX, mouseY, activate);
        }

        @Override
        public void hover(boolean value) {
            if (value != lastHovered) {
                hoverChanges++;
                lastHovered = value;
            }
            super.hover(value);
        }

        @Override
        protected void onActivate() {
            activates++;
        }

        @Override
        protected void paint(PaintCtx ctx) {
        }
    }
}
