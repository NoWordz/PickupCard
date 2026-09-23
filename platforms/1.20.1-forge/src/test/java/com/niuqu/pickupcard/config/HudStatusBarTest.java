package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.HudStatusBar;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.tokens.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * A-24：<b>常驻 HUD 形态</b>的离线验收。<b>零 MC、零 GPU、零时钟</b> —— 建树走静态的
 * {@link HudStatusBar#build}（先例是 {@code CardGridTreeTest}），所以这些断言在没启动游戏时
 * 也跑得起来。
 *
 * <p>【这一轮要钉的和前三个形态都不一样】配置列 / 编辑场 / 网格的几何都是"视口多大、
 * 内容就铺多大"；HUD 上<b>反过来</b> —— 视口是整屏，而状态条必须<b>贴在自己的内容上</b>，
 * 否则一条 1280 宽的背板会把世界压暗一条。所以承重的是
 * {@link #thePanelIsTheSameSizeOnATinyCanvasAsOnABigOne()}：
 * <b>同一档 u 下换个视口，面板的矩形一个字都不许变</b>。
 *
 * <p>⚠️【本文件被评审否定过一次，值得记住为什么】第一版里有<b>两条恒真</b>的测试：
 * `build()` 内部那句"建完当场布局"用的是 `layout(bar, 0f, 0f, 1f)`，**视口参数从没被用过**，
 * 于是那两条"换视口看面板变不变"的测试其实在<b>变化一个被忽略的参数</b> —— 换任何错误实现
 * 都照样绿。同一次评审还发现提示盒的宽是从 token 猜的（24px），而 en_us 的 `"G config"`
 * 实测 41px，字形缝把它缩到了 58% —— <b>缩成一团糊字，而且什么都不报</b>。
 * 两条的修法都不是"改断言"，是<b>把接进来的信息真用上</b>。
 */
class HudStatusBarTest {

    private static final float EPS = 0.001f;

    /** 真机那一档：1280×720 @ guiScale 1 → 逻辑画布 1280×720，u 在上限 2.0。 */
    private static final float CANVAS_W = 1280f;
    private static final float CANVAS_H = 720f;
    private static final float U = Tokens.Unit.BASE;

    /**
     * 提示盒的宽 —— <b>离线用假值，真机上由 {@code mc.font.width(串)} 量出来</b>。
     * 取 41 是因为那就是 en_us 的 {@code "G config"} 在 MC 默认字体下的实测宽（评审从
     * 真机截图的墨迹列反解出来的）。测试用这个数，图的是"和真机同一量级"，
     * 顺带钉住"盒子宽 = 传进来的那个数"这条契约。
     */
    private static final float HINT_W = 41f;

    /** 按给定的 u 现造调色板 —— 调色板的半径等角色是 u 的函数，不能跨档复用一份。 */
    private static HudStatusBar.Bar bar(float u, float w, float h) {
        return HudStatusBar.build("hud-count", () -> "5 hint(s)", HINT_W,
                w, h, u, NvgPalette.dark(StyleModel.Accents.defaults(), u));
    }

    private static HudStatusBar.Bar bar() {
        return bar(U, CANVAS_W, CANVAS_H);
    }

    @Test
    @DisplayName("面板贴在自己的内容上，不铺满视口")
    void thePanelHugsItsContentInsteadOfTheViewport() {
        HudStatusBar.Bar b = bar();
        Rect panel = b.panelBox();
        Rect chip = b.chipBox();
        Rect hint = b.hintBox();
        float pad = Tokens.Space.STEP_2 * U;
        float gap = Tokens.Space.STEP_2 * U;

        // 逐项复算：内边距 ×2 + 芯片 + 缝 + 提示盒。差一点点就说明面板不是自然尺寸。
        assertEquals(2f * pad + chip.width() + gap + hint.width(), panel.width(), EPS,
                "面板宽必须等于两段内容 + 一条缝 + 两个内边距");
        // 【判别力在这一条】铺满视口的话这里是 1280，而自然尺寸远小于它。
        assertTrue(panel.width() < CANVAS_W / 4f,
                "面板不该铺满视口：宽=" + panel.width() + "（视口 " + CANVAS_W + "）");
        // 高只该是一行的 ROW_H + 上下内边距，绝不该是画布高。
        assertEquals(chip.height() + 2f * pad, panel.height(), EPS, "面板高 = 一行 + 上下内边距");
    }

    @Test
    @DisplayName("面板落在左上的留白处（不是 (0,0)）")
    void thePanelSitsAtTheTopLeftInset() {
        Rect panel = bar().panelBox();
        float inset = Tokens.Space.STEP_3 * U;
        assertEquals(inset, panel.x(), EPS, "面板左缘 = 根那层留白");
        assertEquals(inset, panel.y(), EPS, "面板上缘 = 根那层留白");
    }

    @Test
    @DisplayName("芯片的尺寸来自 token；提示盒的宽来自**实量**（不是 token 猜的）")
    void chipIsTokenSizedAndTheHintBoxIsMeasured() {
        HudStatusBar.Bar b = bar();
        Rect chip = b.chipBox();
        assertEquals(Tokens.Size.CONTROL_MIN_W * U, chip.width(), EPS, "芯片宽 = 控件最小宽 × u");
        assertEquals(Tokens.Size.ROW_H * U, chip.height(), EPS, "芯片高 = 一行 × u");
        // 提示盒**不**等于 LABEL_MIN_W × u —— 第一版就是那么写的，结果把 41px 的字塞进 24px。
        assertEquals(HINT_W, b.hintBox().width(), EPS, "提示盒宽 = 传进来的量宽");
        assertTrue(b.hintBox().width() > Tokens.Size.LABEL_MIN_W * U,
                "实量宽必须大于那个 token，否则这条测试自己就没意义了");
    }

    @Test
    @DisplayName("提示盒跟着量宽走：两个不同的量宽给出两个不同的盒宽")
    void theHintBoxHonoursTheMeasuredWidth() {
        float narrow = HudStatusBar.build("hud-count", () -> "5 hint(s)", 20f,
                CANVAS_W, CANVAS_H, U, NvgPalette.dark(StyleModel.Accents.defaults(), U))
                .hintBox().width();
        float wide = HudStatusBar.build("hud-count", () -> "5 hint(s)", 60f,
                CANVAS_W, CANVAS_H, U, NvgPalette.dark(StyleModel.Accents.defaults(), U))
                .hintBox().width();
        assertEquals(20f, narrow, EPS, "量宽 20 就该给 20");
        assertEquals(60f, wide, EPS, "量宽 60 就该给 60");
        // 【判别力】写成 token 猜的话两次都是同一个数，这一条当场红。
        assertTrue(wide > narrow, "盒宽必须真的跟着量宽变");
    }

    @Test
    @DisplayName("同一档 u 下，320×180 与 1280×720 上的面板矩形逐位相同")
    void thePanelIsTheSameSizeOnATinyCanvasAsOnABigOne() {
        Rect big = bar(U, CANVAS_W, CANVAS_H).panelBox();
        Rect small = bar(U, 320f, 180f).panelBox();
        // 【本形态的承重判据】HUD 的视口是整屏，面板必须完全不理会它。
        // 只要有人把面板的 Sizing 写成铺满/随视口，这一条当场红。
        assertEquals(big.x(), small.x(), EPS, "x 与视口无关");
        assertEquals(big.y(), small.y(), EPS, "y 与视口无关");
        assertEquals(big.width(), small.width(), EPS, "宽与视口无关");
        assertEquals(big.height(), small.height(), EPS, "高与视口无关");
    }

    @Test
    @DisplayName("u 驱动的部分跟着 u 走，实量出来的提示宽不跟着走")
    void uDrivesTheTokenSizedPartsOnly() {
        HudStatusBar.Bar big = bar(Tokens.Unit.BASE, CANVAS_W, CANVAS_H);
        HudStatusBar.Bar small = bar(Tokens.Unit.MIN, CANVAS_W, CANVAS_H);

        // 芯片是 token 尺寸 → 随 u 缩放。**但要按真矩形断言，不是按公式推**：
        // `Snapping` 是摆放**之后**的事后一遍（坑 22），每个节点四边**各自**取最近。
        // 1.5 档的账（本喵手算过两次，机器报的实际值就是 13）：
        //   上缘 y = inset(3×1.5) + pad(2×1.5) = 7.5 → round → **8**
        //   下缘   = 7.5 + ROW_H(9)×1.5 = 21.0 → round → **21**
        //   高 = 21 − 8 = **13**（不是 13.5、也不是 14 —— 差值来自两次独立的取整）
        // 这正是"读数要读真矩形；重算出来的数不是任何东西的实际位置"。
        assertEquals(48f, big.chipBox().width(), EPS, "2.0 档芯片宽 = 24×2");
        assertEquals(36f, small.chipBox().width(), EPS, "1.5 档芯片宽 = 24×1.5");
        assertEquals(18f, big.chipBox().height(), EPS, "2.0 档芯片高 = 9×2");
        assertEquals(13f, small.chipBox().height(), EPS, "1.5 档芯片高：8→21 两次吸附之差 = 13");
        // 提示盒**不**随 u 变：它的宽是字体量出来的，而 MC 的字号不随 u 走。
        // 【为什么这条要单独钉】本喵第一版写的断言是"整条面板随 u 线性缩放" —— 提示盒
        // 改成实量之后它当场红了。**红的是假设，不是代码**：面板宽 = 随 u 的部分 + 不随 u 的
        // 提示宽，本来就不该线性。这条测试现在钉的才是那个真不变量。
        assertEquals(big.hintBox().width(), small.hintBox().width(), EPS,
                "提示盒宽是实量的，不该被 u 缩放");
    }

    @Test
    @DisplayName("一次输入都不给：没有悬停、没有焦点，布局与 tick 也不炸")
    void nothingIsHoveredOrFocusedWithoutAnyInput() {
        HudStatusBar.Bar b = bar();
        b.tree().tick(1_000_000_000L);
        assertNull(b.tree().hovered(), "没人指过任何地方，就不该有悬停对象");
        assertNull(b.tree().focused(), "没人按过 Tab，就不该有焦点");
    }

    @Test
    @DisplayName("视口比面板还窄时不产生负尺寸矩形（退化输入）")
    void aTinyViewportDoesNotProduceANegativeRect() {
        // 窄到连留白都放不下：面板仍然是自然尺寸、溢出到右边 —— 但**绝不能**出现负宽高
        // （负尺寸会当场抛，见 A-23 真机第 62 轮那条：切换行不可见时下游减法变负）。
        HudStatusBar.Bar b = bar(U, 4f, 4f);
        Rect panel = b.panelBox();
        assertTrue(panel.width() > 0f, "宽必须是正的：" + panel.width());
        assertTrue(panel.height() > 0f, "高必须是正的：" + panel.height());
        assertTrue(b.chipBox().width() > 0f, "芯片宽必须是正的");
        assertTrue(b.hintBox().width() > 0f, "提示盒宽必须是正的");
    }

    @Test
    @DisplayName("读数与真矩形逐字相同：dump() 整串等于用 bounds() 拼出来的那一串")
    void theDumpReadsTheRealRects() {
        HudStatusBar.Bar b = bar();
        Rect p = b.panelBox();
        Rect c = b.chipBox();
        Rect h = b.hintBox();
        // 【为什么断言整串，而不是 contains 一个前缀】评审逮到过第一版：它只断言
        // `dump().contains("HUD 状态条")` —— 那是在钉"日志前缀"这个 grep 约定，
        // **dump 里那 8 个数一个都没被看过**。整串相比，任何"读数另算一份几何"的写法都会红。
        String expected = String.format(Locale.ROOT,
                "HUD 状态条: 面板=%.0fx%.0f@(%.0f,%.0f) 芯片=%.0fx%.0f@(%.0f,%.0f)"
                        + " 提示盒=%.0fx%.0f@(%.0f,%.0f) 盒间缝=%.0f",
                p.width(), p.height(), p.x(), p.y(),
                c.width(), c.height(), c.x(), c.y(),
                h.width(), h.height(), h.x(), h.y(),
                h.x() - (c.x() + c.width()));
        assertEquals(expected, b.dump(), "读数必须就是这几个矩形的原文");
    }
}
