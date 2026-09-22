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
