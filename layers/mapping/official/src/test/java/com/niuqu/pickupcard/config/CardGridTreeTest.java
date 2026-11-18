package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.layout.GridMath;
import com.niuqu.pickupcard.render.nvg.ui.CardGridTree;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.widget.Widget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A-19：网格形态的离线验收。<b>零 MC、零 GPU、零时钟</b> —— 建树走静态的
 * {@link CardGridTree#build}（先例是 {@code TrellisScrollHitTest} 调 {@code TrellisColumn.buildColumn}），
 * 所以这些断言在没启动游戏时也跑得起来。
 *
 * <p>【最要紧的一条是 {@link #scrollerTakesTheRemainingHeightNotTheContentHeight()}】
 * 滚动容器用的是 {@code Sizing.fixed(0f) + withGrow(1f)}，这个组合在本仓库此前<b>零使用、
 * 零测试</b>（{@code fixed(0f)} 全仓 grep 无命中）。单独用 {@code withGrow(1f)} 会静默失效：
 * 容器的自然高 = 内容全高 → {@code used} 超过可用高 → {@code distributeGrow} 在
 * {@code free <= 0} 时直接返回 → 容器被摆成内容那么高、冲出屏幕，且 {@code maxOffsetY} 恒为 0
 * （<b>滚不动，还不报错</b>）。这条测试就是钉住它，而且它必须能<b>区分</b>两种结果 ——
 * 所以断言的是"等于剩余高"且"不等于内容高"。
 *
 * <p>【A-22 之后多了一条承重的：{@link #theRaggedLastRowKeepsTheFullRowWidth()}】格子宽从
 * {@code Sizing.fixed(调用方算的 cellW)} 换成了 {@code Sizing.track(cols)}（缝由父容器扣），
 * 于是 {@code Align.STRETCH} 从"好看"升成了<b>承重</b>：轨道等分的分母就是父容器给的宽。
 * 因此"内容盒被拉满"那条<b>丧失判别力</b>了（轨道行的自然宽本身就是可用宽），
 * 判别力搬到了末行那条上 —— 去掉 STRETCH，末行格子会只剩一半宽。
 */
class CardGridTreeTest {

    private static final float EPS = 0.001f;

    /** 真机那一档：1280×720 的窗口 @ guiScale 1 → 逻辑画布 1280×720，u 在上限 2.0。 */
    private static final float CANVAS_W = 1280f;
    private static final float CANVAS_H = 720f;
    private static final float GUI_SCALE = 1f;
    private static final float U = Tokens.Unit.BASE;
    private static final NvgPalette TEST_PALETTE = NvgPalette.dark(StyleModel.Accents.defaults(), U);

    /**
     * 四档真实画布：视口宽高、u、guiScale、列数、<b>A-19 记的旧值</b>（调用方 floor 到整数）、
     * <b>A-22 的槽宽真值</b>（L1 的轨道槽宽，吸附前）。
     * <p>前四档就是 harness 的 {@code -PharnessGuiScale=1..4}；旧值就是 A-19 真机读数里的
     * {@code 314 / 207 / 137 / 154}，留着当基准钉差异 —— 新口径只该"把旧口径丢掉的余数还回来"，
     * 不该出现别的变化。
     */
    private record Tier(float width, float height, float u, float guiScale, int cols,
                        float oldFloor, float slot) {
    }

    private static final Tier[] TIERS = {
            new Tier(1280f, 720f, 2.0f, 1f, 4, 314f, 314.0f),
            new Tier(640f, 360f, 1.8f, 2f, 3, 207f, 207.33333f),
            new Tier(426.7f, 240f, 1.5f, 3f, 3, 137f, 137.23333f),
            new Tier(320f, 180f, 1.5f, 4f, 2, 154f, 154.0f),
    };

    /** 一格都没有、也不画东西的替身（与 {@code TrellisScrollHitTest} 同款）。 */
    private static List<Widget> inert(int count) {
        List<Widget> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new TestWidgets.Inert());
        }
        return items;
    }

    private static CardGridTree.Grid grid(int count) {
        return grid(count, CANVAS_W, CANVAS_H, U);
    }

    private static CardGridTree.Grid grid(int count, float width, float height, float u) {
        return CardGridTree.build(inert(count), width, height, u, TEST_PALETTE);
    }

    private static void relayout(CardGridTree.Grid g) {
        CardGridTree.layout(g, CANVAS_W, CANVAS_H, 1f / GUI_SCALE);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("根就是视口：它自己那份矩形等于整屏（视口的尺寸是权威的）")
    void rootIsTheViewport() {
        CardGridTree.Grid g = grid(24);
        Rect root = g.tree().root().bounds();
        assertEquals(0f, root.x(), EPS);
        assertEquals(0f, root.y(), EPS);
        assertEquals(CANVAS_W, root.width(), EPS);
        assertEquals(CANVAS_H, root.height(), EPS);
    }

    @Test
    @DisplayName("★ 滚动容器拿的是【剩余高】，不是【内容高】（fixed(0)+grow 那条承重墙）")
    void scrollerTakesTheRemainingHeightNotTheContentHeight() {
        CardGridTree.Grid g = grid(24);
        float pad = Tokens.Space.STEP_3 * U;
        float rowH = Tokens.Size.ROW_H * U;
        float expected = CANVAS_H - 2f * pad - 2f * rowH;

        assertEquals(expected, g.viewport().height(), EPS,
                "容器的矩形必须正好是「整屏扣掉上下留白与两行字」的那一段");
        assertTrue(g.viewport().height() < g.scroller().contentHeight() - 1f,
                "这条测试的前提是内容比视口高（否则没什么可滚，「剩余高」也就无从区分）—— "
                        + "现在视口高 " + g.viewport().height() + "、内容高 " + g.scroller().contentHeight());
        assertTrue(g.scroller().maxOffsetY() > 0f,
                "容器拿对了高度才滚得动。若这里是 0，说明 grow 静默失效了"
                        + "（容器被摆成了内容那么高：见 CardGridTree 的类注释）");
    }

    @Test
    @DisplayName("内容盒横向铺满：宽 = 视口宽 − 2×左右内边距（内边距只减一次）")
    void contentFillsTheViewportWidthMinusPadding() {
        // 【这条在 A-22 之后不再能证明 STRETCH】轨道行的自然宽本身就是可用宽，所以去掉内容盒的
        // STRETCH 它照样绿 —— 从前 1280 那档会因此变成恒真，本喵当时特意换成了 1281；
        // 现在换哪一档都一样。留着它是为了钉"内边距只减一次"（那个错会让格子左缘比标题多一格），
        // STRETCH 的判别力在下面 theRaggedLastRowKeepsTheFullRowWidth 那条。
        CardGridTree.Grid g = grid(24, 1281f, CANVAS_H, U);
        float pad = Tokens.Space.STEP_3 * U;
        Rect content = g.scroller().children().get(0).bounds();

        assertEquals(1281f - 2f * pad, content.width(), EPS,
                "内容盒横向没铺满 —— 内边距被减了两次？");
    }

    @Test
    @DisplayName("★ 末行格子与满行等宽（A-22 之后 STRETCH 是承重的）")
    void theRaggedLastRowKeepsTheFullRowWidth() {
        CardGridTree.Grid g = grid(22);       // 4 列 → 5 行满 + 末行 2 格
        assertEquals(4, g.metrics().cols(), "这一档应当解出 4 列");
        float full = g.cells().get(0).bounds().width();
        Rect last = g.cells().get(g.cells().size() - 1).bounds();
        Rect content = g.scroller().children().get(0).bounds();

        assertEquals(full, last.width(), EPS,
                "末行格子的宽与满行不同 —— 若这里是满行的一半，说明行盒没拿到一个确定的宽度"
                        + "（轨道等分的分母就是它，而轨道只认「父容器给了多少」）");
        assertTrue(last.right() < content.right() - 1f,
                "末行只有 2 格，余数该留在右边（行用 Justify.START），不该被摊进缝里");
    }

    @Test
    @DisplayName("★ 四档真实画布：格子实宽 = L1 的轨道槽宽（与 A-19 的旧值钉差异）")
    void cellWidthComesFromTheFrameworkTrack() {
        // 【为什么这里要关掉吸附】不关的话量到的是"槽宽 + 四边各自吸附"两件事叠在一起的结果
        // （实测 guiScale 2 那档会得到 208.0 而不是槽宽 207.333）—— 隔离变量，坑 17 的口径。
        // 吸附之后真机读数是多少，由下面 theRealMachineReadoutIsTheSnappedWidth 那条管。
        for (Tier t : TIERS) {
            CardGridTree.Grid g = grid(24, t.width(), t.height(), t.u());
            CardGridTree.layout(g, t.width(), t.height(), 0f);
            assertEquals(t.cols(), g.metrics().cols(),
                    "画布 " + t.width() + " 宽（u=" + t.u() + "）的列数不对");
            float real = g.cellWidth();

            assertEquals(t.slot(), real, 0.01f,
                    "画布 " + t.width() + " 宽（u=" + t.u() + "）的格子实宽与轨道槽宽不符");
            assertTrue(real >= t.oldFloor(),
                    "新口径只该把旧 floor 丢掉的余数还回来，不该更小：" + real + " vs " + t.oldFloor());
            assertTrue(real - t.oldFloor() < 1f,
                    "旧口径丢掉的余数不会到一个整像素：" + real + " vs " + t.oldFloor());
            if (t.oldFloor() == t.slot()) {
                assertEquals(t.oldFloor(), real, EPS,
                        "这一档除得尽，槽宽该与 A-19 的记录逐位相同");
            }
        }
    }

    @Test
    @DisplayName("真机读数就是【吸附之后】的值：四档逐档等于实测读数")
    void theRealMachineReadoutIsTheSnappedWidth() {
        // 【为什么钉的是这四个数】它们就是 harness 日志里「每格=」那一段的真值，
        // A-22 真机实测（2026-09-23，run_grid1..4.log）逐档吻合。
        // 钉死它比钉"差值不超过一个步长"强得多 —— 后者在"吸附根本没生效"时也是绿的
        // （0 ≤ 步长 恒真，独立评审实测）。吸附的机制由隔壁那条（关掉吸附量槽宽）
        // 与框架的 SnappingTest 管，这里只管"真机上读出来是几"。
        float[] snapped = {314.0f, 207.0f, 137.0f, 154.0f};
        for (int i = 0; i < TIERS.length; i++) {
            Tier t = TIERS[i];
            CardGridTree.Grid g = grid(24, t.width(), t.height(), t.u());
            float step = 1f / t.guiScale();
            CardGridTree.layout(g, t.width(), t.height(), step);
            float real = g.cellWidth();

            assertEquals(snapped[i], real, EPS,
                    "画布 " + t.width() + "（guiScale " + t.guiScale() + "）的吸附后实宽与真机读数"
                            + "不符；槽宽 " + t.slot() + "、步长 " + step);
            assertTrue(Math.abs(real - t.slot()) <= step + EPS,
                    "包络：吸附只让每条边挪半格以内（实宽 " + real + "、槽宽 " + t.slot() + "）");
        }
    }

    @Test
    @DisplayName("同一行里每格等宽；末行首格与首行首格左缘对齐")
    void cellsShareWidthAndLeftEdge() {
        CardGridTree.Grid g = grid(22);       // 4 列 → 末行只有 2 格
        int cols = g.metrics().cols();
        assertEquals(4, cols, "这一档应当解出 4 列");

        float expected = g.cells().get(0).bounds().width();
        for (int i = 0; i < g.cells().size(); i++) {
            Rect box = g.cells().get(i).bounds();
            assertEquals(expected, box.width(), EPS, "第 " + i + " 格宽不等");
            assertEquals(g.metrics().cellHeight(), box.height(), EPS, "第 " + i + " 格高不等");
            if (i % cols == 0) {
                assertEquals(g.cells().get(0).bounds().x(), box.x(), EPS,
                        "第 " + (i / cols) + " 行首格左缘与首行不齐");
            }
        }
        // 22 个 = 5 行满 + 末行 2 个
        assertEquals(6, g.cells().size() / cols + (g.cells().size() % cols == 0 ? 0 : 1));
        assertEquals(6, g.metrics().rows());
    }

    @Test
    @DisplayName("每个项目都有自己的一格，顺序是行优先")
    void everyItemGetsItsOwnCell() {
        CardGridTree.Grid g = grid(10);
        assertEquals(10, g.cells().size());
        // 行优先：第 i 格的 y 随 i/cols 递增、x 随 i%cols 递增
        int cols = g.metrics().cols();
        for (int i = 1; i < g.cells().size(); i++) {
            Rect prev = g.cells().get(i - 1).bounds();
            Rect box = g.cells().get(i).bounds();
            if (i % cols == 0) {
                assertTrue(box.y() > prev.y(), "第 " + i + " 格没换行");
                assertEquals(g.cells().get(0).bounds().x(), box.x(), EPS, "换行后没回到左缘");
            } else {
                assertTrue(box.x() > prev.x(), "第 " + i + " 格没往右走");
                assertEquals(prev.y(), box.y(), EPS, "同一行里 y 不一致");
            }
        }
    }

    @Test
    @DisplayName("格子矩形与手算逐位一致（布局给了我们声明的那个数，没被夹）")
    void cellBoundsMatchTheDeclaredGeometry() {
        CardGridTree.Grid g = grid(24);
        GridMath.Metrics m = g.metrics();
        Rect first = g.cells().get(0).bounds();
        // 首格左缘 = 根 padding + 内容盒横向 0；上缘 = 根 padding + 标题 + 内容上留白
        float pad = Tokens.Space.STEP_3 * U;
        float gap = Tokens.Space.STEP_2 * U;
        float rowH = Tokens.Size.ROW_H * U;
        assertEquals(pad, first.x(), EPS, "首格左缘");
        assertEquals(pad + rowH + gap, first.y(), EPS, "首格上缘（标题之下、内容上留白之后）");
        // 第二格：右移一格宽 + 一条缝（格宽读第一格自己的矩形，不重算）
        Rect second = g.cells().get(1).bounds();
        assertEquals(first.x() + first.width() + gap, second.x(), EPS,
                "第二格没按「格宽 + 缝」右移");
        // 第二行首格：下移一格高 + 一条缝
        Rect secondRow = g.cells().get(m.cols()).bounds();
        assertEquals(first.y() + m.cellHeight() + gap, secondRow.y(), EPS, "第二行没按「格高 + 缝」下移");
    }

    @Test
    @DisplayName("退让真的发生：同一块画布上，宽档比窄档列数多")
    void narrowerCanvasGetsFewerColumns() {
        CardGridTree.Grid wide = grid(24, 1280f, 720f, U);
        CardGridTree.Grid narrow = grid(24, 320f, 180f, U);
        assertTrue(wide.metrics().cols() > narrow.metrics().cols(),
                "1280 宽给了 " + wide.metrics().cols() + " 列、320 宽给了 " + narrow.metrics().cols()
                        + " 列 —— 退让没发生");
    }

    // ------------------------------------------------------------------
    // 滚动
    // ------------------------------------------------------------------

    @Test
    @DisplayName("滚动范围 = 内容高 − 视口高；装得下时为 0（不出现「能滚一点点」）")
    void scrollRangeIsContentMinusViewport() {
        CardGridTree.Grid g = grid(24);
        relayout(g);
        assertEquals(g.scroller().contentHeight() - g.viewport().height(),
                g.scroller().maxOffsetY(), EPS);

        // 只有 4 个（1 行）时内容比视口矮 → 不该能滚
        CardGridTree.Grid tiny = grid(4);
        relayout(tiny);
        assertEquals(0f, tiny.scroller().maxOffsetY(), EPS, "装得下却还能滚一截");
    }

    @Test
    @DisplayName("★ 滚出视口的格子点不到（二维上的「祖先矩形必须包含该点」）")
    void scrolledOutCellsAreNotHittable() {
        CardGridTree.Grid g = grid(24);
        g.scroller().scrollTo(0f, 200f);
        relayout(g);

        Rect view = g.viewport();
        Rect first = g.cells().get(0).bounds();
        float cx = first.x() + first.width() / 2f;
        // 视口上方半格处：那里有一格（它的矩形在树里），但已经滚出去了
        assertNull(g.tree().hitTest(cx, view.y() - g.cellHeight() / 2f),
                "视口上方的点命中了已经滚出去的格子 —— 那些格子根本看不见");
        // 视口内第一行仍然点得到
        assertNotNull(g.tree().hitTest(cx, view.y() + g.cellHeight() / 2f),
                "视口里看得见的格子却点不到");
    }

    // ------------------------------------------------------------------
    // 导航
    // ------------------------------------------------------------------

    @Test
    @DisplayName("★ 二维证明：从 (0,0) 按下走到 (1,0)，不是走到 (0,1)")
    void downMovesToTheNextRowNotTheNextIndex() {
        CardGridTree.Grid g = grid(24);
        int cols = g.metrics().cols();
        assertEquals(4, cols, "这一档应当 4 列");

        g.tree().requestFocus(g.cells().get(0));
        assertEquals(0, g.focusedIndex());

        assertTrue(CardGridTree.navigate(g, 0, 1), "按下应当有去处");
        assertEquals(cols, g.focusedIndex(),
                "从第 0 格按下应当到第 " + cols + " 格（下一行同一列）。"
                        + "若这里是 1，说明它只是「序号 + 1」，那仍是一维的");
        assertEquals("焦点=行1列0", CardGridTree.focusDump(g));
    }

    @Test
    @DisplayName("右移在同一行里走一格")
    void rightStaysInTheSameRow() {
        CardGridTree.Grid g = grid(24);
        g.tree().requestFocus(g.cells().get(0));
        assertTrue(CardGridTree.navigate(g, 1, 0));
        assertEquals("焦点=行0列1", CardGridTree.focusDump(g));
    }

    @Test
    @DisplayName("边界不环绕：第一行按上、最左按左，都停在原地")
    void navigationDoesNotWrap() {
        CardGridTree.Grid g = grid(24);
        int cols = g.metrics().cols();

        g.tree().requestFocus(g.cells().get(0));
        assertTrue(!CardGridTree.navigate(g, 0, -1), "第一行按上不该有去处");
        assertEquals(0, g.focusedIndex(), "第一行按上必须停在原地（环绕看起来像 bug）");
        assertTrue(!CardGridTree.navigate(g, -1, 0), "最左按左不该有去处");
        assertEquals(0, g.focusedIndex());

        // 最右按右也不动
        g.tree().requestFocus(g.cells().get(cols - 1));
        assertTrue(!CardGridTree.navigate(g, 1, 0), "最右按右不该有去处");
        assertEquals(cols - 1, g.focusedIndex());
    }

    @Test
    @DisplayName("末行不足时：往右到不了不存在的格子，往下也到不了")
    void navigationStopsAtTheRaggedLastRow() {
        // 22 个 = 5 行满 + 末行 2 个（下标 20、21）
        CardGridTree.Grid g = grid(22);
        assertEquals(4, g.metrics().cols());

        g.tree().requestFocus(g.cells().get(20));
        assertEquals("焦点=行5列0", CardGridTree.focusDump(g));
        assertTrue(CardGridTree.navigate(g, 1, 0), "末行第二格是存在的");
        assertEquals("焦点=行5列1", CardGridTree.focusDump(g));
        assertTrue(!CardGridTree.navigate(g, 1, 0), "末行只有 2 格，再往右不该有去处");
        assertTrue(!CardGridTree.navigate(g, 0, 1), "末行下面没有了");
    }

    @Test
    @DisplayName("没有焦点时导航不动（不自己挑一格出来）")
    void navigationWithoutFocusDoesNothing() {
        CardGridTree.Grid g = grid(24);
        assertEquals(-1, g.focusedIndex());
        assertTrue(!CardGridTree.navigate(g, 1, 0));
        assertEquals(-1, g.focusedIndex());
    }

    // ------------------------------------------------------------------
    // 读数
    // ------------------------------------------------------------------

    @Test
    @DisplayName("定妆读数把列数与每格尺寸都报出来（读数与测试读同一批字符串）")
    void dumpReportsTheColumnCount() {
        CardGridTree.Grid g = grid(24);
        String dump = CardGridTree.dump(g);
        assertTrue(dump.contains("列数=4"), "读数里没有列数： " + dump);
        // A-22 之后每格宽印一位小数、读的是格子的真矩形；这一档除得尽，与 A-19 记录同值
        assertTrue(dump.contains("每格=314.0x180"), "每格尺寸与记录不符： " + dump);
        assertTrue(dump.contains("格子=24"), "格子数不对： " + dump);
    }

    @Test
    @DisplayName("★ 焦点走到视口外时视口跟着滚（A-21 把 scrollIntoView 补上了）")
    void focusOutsideTheViewportScrollsItIntoView() {
        CardGridTree.Grid g = grid(24);
        g.tree().requestFocus(g.cells().get(0));
        assertEquals(0f, g.scroller().offsetY(), EPS, "一开始没滚过");

        // 一路按到视口外（4 列 × (180 + 4) 一行，视口高 672 → 第 4 行出头）
        for (int i = 0; i < 4; i++) {
            CardGridTree.navigate(g, 0, 1);
        }
        String probe = CardGridTree.scrollProbeDump(g);
        assertTrue(probe.contains("焦点=行4列0"), "焦点没往下走： " + probe);
        assertTrue(g.scroller().offsetY() > 0f,
                "焦点到行 4 已经在视口外，视口必须跟过去（A-19 时这里恒为 0，"
                        + "玩家看到的是「焦点环没了」，以为键盘失灵）： " + probe);

        // 不变量：焦点那一格现在**完整**落在视口里（推够就行，不要求居中）。
        // 要 relayout 一次才看得见 —— 偏移改的是字段，子节点矩形下一趟布局才跟着动。
        relayout(g);
        assertFocusedCellFullyVisible(g);
        assertTrue(g.cells().get(g.focusedIndex()).bounds().y() <= g.viewport().bottom(),
                "焦点格跑到视口下面去了");
    }

    @Test
    @DisplayName("回头往上走时视口也跟回去（不是只会往下滚）")
    void focusMovingBackUpScrollsBack() {
        CardGridTree.Grid g = grid(24);
        g.tree().requestFocus(g.cells().get(0));
        for (int i = 0; i < 4; i++) {
            CardGridTree.navigate(g, 0, 1);
        }
        assertTrue(g.scroller().offsetY() > 0f, "前提：先滚下去了");
        for (int i = 0; i < 4; i++) {
            CardGridTree.navigate(g, 0, -1);
        }
        assertEquals("焦点=行0列0", CardGridTree.focusDump(g));
        // 【为什么不是正好 0】推的是"最小位移"—— 把目标顶到视口上沿就停；而内容顶上还有
        // 4px 内边距（内容盒的 padding-top = 缝隙），所以停在 4。与浏览器的
        // `scrollIntoView({ block: 'nearest' })` 同一口径：够用就停，不做"回到起点"这种额外动作。
        assertTrue(g.scroller().offsetY() < 8f,
                "回到第一行，偏移该基本回到起点，实际 " + g.scroller().offsetY());
        relayout(g);
        assertFocusedCellFullyVisible(g);
    }

    @Test
    @DisplayName("同一屏内换焦点时一动不动（否则画面会一直微微抖）")
    void focusInsideTheViewportDoesNotScroll() {
        CardGridTree.Grid g = grid(24);
        g.tree().requestFocus(g.cells().get(0));
        for (int i = 0; i < 3; i++) {
            CardGridTree.navigate(g, 1, 0);
        }
        assertEquals("焦点=行0列3", CardGridTree.focusDump(g));
        assertEquals(0f, g.scroller().offsetY(), EPS,
                "第 0 行本来就在视口里，挪焦点不该动视口");
    }

    /** 焦点那一格必须完整落在视口里 —— A-21 的那条不变量。 */
    private static void assertFocusedCellFullyVisible(CardGridTree.Grid g) {
        Rect box = g.cells().get(g.focusedIndex()).bounds();
        Rect view = g.viewport();
        assertTrue(box.y() >= view.y() - EPS && box.y() + box.height() <= view.y() + view.height() + EPS,
                "焦点格没被完整露出来：格 y " + box.y() + ".." + (box.y() + box.height())
                        + "，视口 y " + view.y() + ".." + (view.y() + view.height()));
    }
}
