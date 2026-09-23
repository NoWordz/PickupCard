package com.niuqu.pickupcard.render.nvg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.widget.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A-27：<b>第五个形态（模态 / 对话框）</b>的离线验收。<b>零 MC、零 GPU、零时钟</b> ——
 * 建树走静态的 {@link ConfirmModal#build}（先例是 {@code HudStatusBarTest} / {@code CardGridTreeTest}）。
 *
 * <p>【这一形态要验的和前四个都不同】前四个是"一棵树、换形状 / 换前提"；这一形态第一次出现
 * <b>同屏两棵树</b>，要验的是它们之间那条规矩：<b>输入独占</b> —— 模态开着时，事件全归它，
 * 底下的树一点都收不到。
 *
 * <p>⚠️【"输入独占"在 MC 里是白送的，所以最容易被验成假】{@code Screen} 栈本来就会把事件
 * 只发给最上面那层 —— 压一个 {@code Screen} 当对话框，"底下的收不到"是 <b>MC 在隔离输入</b>，
 * 不是框架做对了什么（A-17 的编辑场就是这么做的）。真正没验过的是：
 * <b>不靠第二个 Screen，框架自己能不能容纳"两棵树 + 由宿主独占路由"</b>。
 * <p><b>本文件钉的是"两棵树各自独立"</b>（{@link #twoTreesCoexistWithoutSharedState()}，
 * 一条防回退的钉子：core 今天没有非 final 静态字段）；<b>"事件到底发给谁"是宿主那一趟的事，
 * 离线验不到</b> —— 它靠真机验（见 A-27 的 harness 读数：面板外点击 / 滚轮 / Esc 三条）。
 */
class ConfirmModalTest {

    private static final float EPS = 0.001f;

    /** 真机那一档：1280×720 @ guiScale 1 → 逻辑画布 1280×720，u 在上限 2.0。 */
    private static final float CANVAS_W = 1280f;
    private static final float CANVAS_H = 720f;
    private static final float U = Tokens.Unit.BASE;

    /** 正文盒的宽高 —— <b>离线用假值，真机上由宿主量出来</b>（{@code font.width} 走换行）。 */
    private static final float MSG_W = 160f;
    private static final float MSG_H = 40f;

    private static ConfirmModal.Modal modal(Runnable confirm, Runnable cancel) {
        return modal(confirm, cancel, MSG_W, MSG_H, U, CANVAS_W, CANVAS_H);
    }

    private static ConfirmModal.Modal modal(Runnable confirm, Runnable cancel,
                                            float msgW, float msgH, float u,
                                            float vw, float vh) {
        ConfirmModal.Modal m = ConfirmModal.build("确认", "取消", confirm, cancel,
                msgW, msgH, Tokens.Size.CONTROL_MIN_W * u, u,
                NvgPalette.dark(StyleModel.Accents.defaults(), u));
        ConfirmModal.layout(m, vw, vh, 1f);
        return m;
    }

    @Test
    @DisplayName("面板落在视口正中（模态的默认位置）")
    void thePanelSitsInTheCentreOfTheViewport() {
        Rect p = modal(null, null).panelBox();
        assertEquals(CANVAS_W / 2f, p.x() + p.width() / 2f, EPS, "面板水平居中");
        assertEquals(CANVAS_H / 2f, p.y() + p.height() / 2f, EPS, "面板垂直居中");
    }

    @Test
    @DisplayName("面板贴内容（自然尺寸），不铺满视口")
    void thePanelHugsItsContent() {
        ConfirmModal.Modal m = modal(null, null);
        Rect p = m.panelBox();
        Rect msg = m.messageBox();
        float pad = Tokens.Space.STEP_4 * U;
        float gap = Tokens.Space.STEP_2 * U;
        float rowH = Tokens.Size.ROW_H * U;
        float buttonRowW = Tokens.Size.CONTROL_MIN_W * U * 2f + gap;

        // 宽 = 两个内边距 + 正文与按钮行里更宽的那个（MSG_W=160 > 按钮行 100）。
        assertEquals(2f * pad + Math.max(MSG_W, buttonRowW), p.width(), EPS,
                "面板宽 = 两段内容里更宽的 + 两个内边距");
        // 高 = 两个内边距 + 正文 + 缝 + 一行钮。
        assertEquals(2f * pad + msg.height() + gap + rowH, p.height(), EPS,
                "面板高 = 正文 + 缝 + 按钮行 + 两个内边距");
        // 【判别力】铺满视口的话这里会是 1280 / 720。
        assertTrue(p.width() < CANVAS_W / 4f, "面板不该铺满视口：宽=" + p.width());
        assertTrue(p.height() < CANVAS_H / 4f, "面板不该铺满视口：高=" + p.height());
    }

    @Test
    @DisplayName("两颗钮同排、等宽、中间隔一条缝（几何来自排布，不是手算）")
    void confirmAndCancelSitSideBySide() {
        ConfirmModal.Modal m = modal(null, null);
        Rect c = m.confirmBox();
        Rect x = m.cancelBox();
        float gap = Tokens.Space.STEP_2 * U;
        float bw = Tokens.Size.CONTROL_MIN_W * U;

        assertEquals(bw, c.width(), EPS, "确认钮宽 = 传进来的那个数");
        assertEquals(bw, x.width(), EPS, "取消钮宽 = 传进来的那个数");
        assertEquals(c.y(), x.y(), EPS, "两颗钮同排（同一 y）");
        assertEquals(gap, x.x() - (c.x() + c.width()), EPS, "两颗钮之间正好一条缝");
        // 按钮行居中：两侧留白相等。
        Rect p = m.panelBox();
        assertEquals(c.x() - p.x() - Tokens.Space.STEP_4 * U,
                p.x() + p.width() - Tokens.Space.STEP_4 * U - (x.x() + x.width()), EPS,
                "按钮行在面板里居中（左边距 = 右边距）");
    }

    @Test
    @DisplayName("点确认 → 焦点落到确认钮，回车触发它（键只到焦点那一个）")
    void clickingConfirmFocusesItAndEnterActivatesIt() {
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        ConfirmModal.Modal m = modal(confirmed::incrementAndGet, cancelled::incrementAndGet);

        Rect c = m.confirmBox();
        m.pointerDown(c.x() + c.width() / 2f, c.y() + c.height() / 2f);
        assertEquals("confirm", m.focusedName(), "点确认钮，焦点就该在它身上");
        m.pointerUp(c.x() + c.width() / 2f, c.y() + c.height() / 2f);
        assertEquals(1, confirmed.get(), "落点还在钮里 = 一次点击");
        assertEquals(0, cancelled.get(), "取消钮没被碰");

        assertTrue(m.keyDown(Keys.ENTER, 0), "回车被焦点钮吃掉");
        assertEquals(2, confirmed.get(), "回车 = 再点一次确认");
        assertEquals(0, cancelled.get(), "取消钮依然没被碰");
    }

    @Test
    @DisplayName("点取消 → 焦点落到取消钮，回车走取消那条路")
    void clickingCancelFocusesItAndEnterCancels() {
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        ConfirmModal.Modal m = modal(confirmed::incrementAndGet, cancelled::incrementAndGet);

        Rect x = m.cancelBox();
        m.pointerDown(x.x() + x.width() / 2f, x.y() + x.height() / 2f);
        assertEquals("cancel", m.focusedName(), "点取消钮，焦点就该在它身上");
        m.pointerUp(x.x() + x.width() / 2f, x.y() + x.height() / 2f);
        assertTrue(m.keyDown(Keys.ENTER, 0));
        assertEquals(0, confirmed.get(), "确认键没被碰");
        assertEquals(2, cancelled.get(), "一次点击 + 一次回车 = 取消两次");
    }

    @Test
    @DisplayName("面板外的点不算「里面」—— 宿主据此把它吃掉且什么也不做")
    void aPointOutsideThePanelIsNotInside() {
        ConfirmModal.Modal m = modal(null, null);
        Rect p = m.panelBox();
        assertTrue(m.hitInside(p.x() + p.width() / 2f, p.y() + p.height() / 2f),
                "正中央当然在里面");
        assertFalse(m.hitInside(p.x() - 8f, p.y() + p.height() / 2f), "左外侧");
        assertFalse(m.hitInside(p.right() + 8f, p.y() + p.height() / 2f), "右外侧");
        assertFalse(m.hitInside(p.x() + p.width() / 2f, p.y() - 8f), "上外侧");
        assertFalse(m.hitInside(p.x() + p.width() / 2f, p.bottom() + 8f), "下外侧");
        // 面板正角上（含边）算里面 —— 与 Rect.contains 的口径一致（半开区间那侧不算）。
        assertTrue(m.hitInside(p.x(), p.y()), "左上角在面板里");
    }

    @Test
    @DisplayName("两棵树共存：建第二棵不会把第一棵弄坏（防回退的钉子 —— core 无非 final 静态字段）")
    void twoTreesCoexistWithoutSharedState() {
        // 第一棵：点确认，留下焦点与几何。
        ConfirmModal.Modal a = modal(null, null, 160f, 40f, U, CANVAS_W, CANVAS_H);
        Rect ca = a.confirmBox();
        a.pointerDown(ca.x() + ca.width() / 2f, ca.y() + ca.height() / 2f);
        assertEquals("confirm", a.focusedName(), "A 的焦点先立住");
        Rect aPanelBefore = a.panelBox();

        // 第二棵：换一个更宽的正文 —— 顺手确认 B 的几何确实独立（与 A 不同）。
        ConfirmModal.Modal b = modal(null, null, 300f, 40f, U, CANVAS_W, CANVAS_H);
        assertTrue(b.panelBox().width() > aPanelBefore.width(),
                "B 的几何独立于 A（这条由断言的构造保证，只是把『不同』写出来）");

        // 【判别力在这一条】若框架里有任何"当前树"式的静态状态（core 今天一个非 final 静态
        // 字段都没有），建 B 就会把 A 的焦点/几何顶掉 —— 那时下面几条当场红。
        // 这不是"证明 core 无静态字段"（那是 grep 的事），而是**防回退**：谁哪天引入一份
        // "当前树"，这条测试会拦住他。
        assertEquals("confirm", a.focusedName(), "建了 B 之后，A 的焦点必须原封不动");
        Rect aPanelAfter = a.panelBox();
        assertEquals(aPanelBefore.x(), aPanelAfter.x(), EPS, "A 的面板 x 不因 B 而变");
        assertEquals(aPanelBefore.y(), aPanelAfter.y(), EPS, "A 的面板 y 不因 B 而变");
        assertEquals(aPanelBefore.width(), aPanelAfter.width(), EPS, "A 的面板宽不因 B 而变");
        assertEquals(aPanelBefore.height(), aPanelAfter.height(), EPS, "A 的面板高不因 B 而变");
        // A 的键路仍然通：回车还是打在 A 自己的确认钮上。
        assertTrue(a.keyDown(Keys.ENTER, 0), "A 的键路不受 B 影响");
        assertEquals("confirm", a.focusedName(), "A 的焦点还在");
    }

    @Test
    @DisplayName("指针移到哪颗钮上，hoveredName() 就报哪颗（悬停只有一份真相，在树里）")
    void hoveredNameFollowsThePointer() {
        ConfirmModal.Modal m = modal(null, null);
        assertEquals("-", m.hoveredName(), "还没指过任何地方");
        Rect c = m.confirmBox();
        m.tree().pointerMove(c.x() + c.width() / 2f, c.y() + c.height() / 2f);
        assertEquals("confirm", m.hoveredName(), "指到确认钮上");
        Rect x = m.cancelBox();
        m.tree().pointerMove(x.x() + x.width() / 2f, x.y() + x.height() / 2f);
        assertEquals("cancel", m.hoveredName(), "移到取消钮上");
        // 面板左上角的空白（正文盒那一带的边上）：两颗钮都不是。
        m.tree().pointerMove(m.panelBox().x() + 2f, m.panelBox().y() + 2f);
        assertEquals("-", m.hoveredName(), "指到面板空白处，没有悬停的钮");
    }

    @Test
    @DisplayName("不给任何输入：没有焦点、不炸")
    void nothingIsFocusedWithoutInput() {
        ConfirmModal.Modal m = modal(null, null);
        m.tree().tick(1_000_000_000L);
        assertEquals("-", m.focusedName(), "没人按过任何地方，就不该有焦点");
    }

    @Test
    @DisplayName("视口比面板还小时不产生负尺寸矩形（退化输入）")
    void aTinyViewportDoesNotProduceANegativeRect() {
        // 窄到连面板都放不下：仍然溢在外面，但**绝不能**出现负宽高（负尺寸会当场抛）。
        ConfirmModal.Modal m = modal(null, null, MSG_W, MSG_H, U, 8f, 8f);
        assertTrue(m.panelBox().width() > 0f, "面板宽必须是正的：" + m.panelBox().width());
        assertTrue(m.panelBox().height() > 0f, "面板高必须是正的：" + m.panelBox().height());
        assertTrue(m.confirmBox().width() > 0f, "确认钮宽必须是正的");
        assertTrue(m.messageBox().height() > 0f, "正文盒高必须是正的");
    }

    @Test
    @DisplayName("u 驱动 token 尺寸的部分；正文盒的宽高是实量的，不跟着 u 走")
    void uDrivesTheTokenSizedPartsOnly() {
        ConfirmModal.Modal big = modal(null, null, MSG_W, MSG_H, Tokens.Unit.BASE, CANVAS_W, CANVAS_H);
        ConfirmModal.Modal small = modal(null, null, MSG_W, MSG_H, Tokens.Unit.MIN, CANVAS_W, CANVAS_H);

        // 钮是 token 尺寸 → 随 u 缩放（按真矩形断言，不按公式推）。
        assertEquals(Tokens.Size.CONTROL_MIN_W * Tokens.Unit.BASE, big.confirmBox().width(), EPS,
                "2.0 档钮宽 = 24×2");
        assertEquals(Tokens.Size.CONTROL_MIN_W * Tokens.Unit.MIN, small.confirmBox().width(), EPS,
                "1.5 档钮宽 = 24×1.5");
        // 正文盒**不**随 u 变：它的宽高是字体量出来的，而 MC 的字号不随 u 走。
        assertEquals(big.messageBox().width(), small.messageBox().width(), EPS,
                "正文盒宽是实量的，不该被 u 缩放");
        assertEquals(big.messageBox().height(), small.messageBox().height(), EPS,
                "正文盒高是实量的，不该被 u 缩放");
    }

    @Test
    @DisplayName("读数与真矩形逐字相同：dump() 整串等于用 bounds() 拼出来的那一串")
    void theDumpReadsTheRealRects() {
        ConfirmModal.Modal m = modal(null, null);
        Rect p = m.panelBox();
        Rect c = m.confirmBox();
        Rect x = m.cancelBox();
        // 【为什么断言整串】只断言前缀的话，dump 里那 12 个数一个都没被看过 ——
        // 任何"读数另算一份几何"的写法都会溜过去（A-24 的评审逮过同款）。
        String expected = String.format(Locale.ROOT,
                "对话框: 面板=%.0fx%.0f@(%.0f,%.0f) 确认=%.0fx%.0f@(%.0f,%.0f)"
                        + " 取消=%.0fx%.0f@(%.0f,%.0f)",
                p.width(), p.height(), p.x(), p.y(),
                c.width(), c.height(), c.x(), c.y(),
                x.width(), x.height(), x.x(), x.y());
        assertEquals(expected, m.dump(), "读数必须就是这几个矩形的原文");
    }
}
