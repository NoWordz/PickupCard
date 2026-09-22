package com.niuqu.pickupcard.render.nvg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/**
 * 按钮的键盘激活（A-17）：焦点在这一格上时 Enter / Space = 点它一下。
 *
 * <p>【为什么现在才有】A-11 / A-15b 之后"焦点"和"键走到谁"才归树管（{@code UiTree.keyDown}
 * 只发给焦点组件）—— 在那之前焦点是控件自己挣的，回车该发给谁没有答案。
 *
 * <p>【为什么这条离线就能验】它不碰绘制，也不碰游戏：{@code keyPressed} 只做一件事 ——
 * 认键、跑动作、说"我吃了"。所以不需要 {@code TrellisWidgetPaintTest} 那套离屏 GL 台子。
 */
class NvgButtonKeyboardTest {

    @Test
    @DisplayName("Enter / 小键盘 Enter / Space 都算按下；别的键不接")
    void activationKeys() {
        int[] hits = {0};
        NvgButton button = new NvgButton("done", () -> "done", () -> hits[0]++);

        assertTrue(button.keyPressed(GLFW.GLFW_KEY_ENTER, 0));
        assertTrue(button.keyPressed(GLFW.GLFW_KEY_KP_ENTER, 0));
        assertTrue(button.keyPressed(GLFW.GLFW_KEY_SPACE, 0));
        assertEquals(3, hits[0], "三个键各该触发一次动作");

        assertFalse(button.keyPressed(GLFW.GLFW_KEY_A, 0),
                "不认识的键要返回 false —— 树才会把它继续往下传（别把整屏的键都吃掉）");
        assertEquals(3, hits[0]);
    }

    /**
     * 【为什么只读钮也要吃掉这两个键】放它漏下去的话，同一个回车会先被这个"看得见却没反应"
     * 的钮没做，再去做别的事（比如编辑场的"回车=存盘"）—— 那比什么都不发生更难解释。
     */
    @Test
    @DisplayName("只读钮（action = null）照样吃掉 Enter/Space，只是不做事")
    void readOnlyButtonSwallowsWithoutActing() {
        NvgButton readOnly = new NvgButton("readonly", () -> "x", null);

        assertTrue(readOnly.keyPressed(GLFW.GLFW_KEY_ENTER, 0));
        assertTrue(readOnly.keyPressed(GLFW.GLFW_KEY_SPACE, 0));
        assertFalse(readOnly.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0));
    }
}
