package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.ui.CardGridTree;
import com.niuqu.pickupcard.render.nvg.ui.McGlyphPainter;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import com.niuqu.pickupcard.render.nvg.ui.PlaceholderCell;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import com.niuqu.pickupcard.render.nvg.ui.WidgetSlot;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.tokens.Units;
import dev.e33.trellis.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>第三个形态试点：网格</b>（A-19，2026-09-22）。
 *
 * <p>【为什么要有这一屏】前两个形态（配置列 {@code PickupCardConfigScreen}、编辑场
 * {@code AnchorEditScreen}）都是<b>一列一屏</b>，框架因此只被两种形状验过。A-17 的教训是
 * <b>换形态最容易把缺件逼出来</b>（它顺手撞出"L4 画不了位图"与"L1 没有绝对定位"两条）。
 * 这一屏是第三种形状，它的主要产出<b>不是界面，是缺件清单</b>（见 {@code docs/plan.md} 的 A-19）。
 *
 * <p>【这一屏只做三件事】把输入推给树、每帧驱动一次、把字画上屏。几何、列数退让、方向键导航
 * 全在 {@link CardGridTree}（静态、离线可测）里 —— 本屏<b>没有</b>自己算过任何一个坐标。
 *
 * <p>【刻意没做的两件】① 格子里是<b>占位格</b>（{@link PlaceholderCell}），不是真卡面：
 * 真卡面要碰 L4 位图那道缺口，会把这一轮从"验形态"漂成"造 L4"；② 焦点走出视口时
 * <b>不自动滚动</b>（框架没有 {@code scrollIntoView}）—— 本轮把它做成 harness 的一条探针读数，
 * 先拿数字再定形状。
 */
public final class CardGridScreen extends Screen {

    /** 占位项目数（真卡面落地后换成玩家的卡列表）。24 = guiScale 1 下正好 6 行 × 4 列。 */
    private static final int PLACEHOLDER_ITEMS = 24;

    private final Screen parent;

    private long now;
    private float u;
    private NvgPalette palette;

    /** 建好的网格树 + 几何（唯一出处）。 */
    private CardGridTree.Grid grid;

    /**
     * harness 定住的指针位置（{@code false} = 用真实鼠标）。
     * <p>【为什么由界面代记】MC 的真实鼠标挪不动，而网格的悬停走的是
     * {@code render(mouseX, mouseY)} 里那个位置 —— 不代记的话"悬停那一格"的截图永远拍不到。
     * 配置屏同款（{@code pointColumnAtForHarness}）。
     */
    private boolean harnessPointerSet;
    private float harnessPointerX;
    private float harnessPointerY;

    public CardGridScreen(Screen parent) {
        super(net.minecraft.network.chat.Component.translatable("pickupcard.grid.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        u = Units.u(this.height);
        palette = NvgPalette.of(CardStage.INSTANCE.previewStyle(), u);
        List<Widget> items = new ArrayList<>(PLACEHOLDER_ITEMS);
        for (int i = 0; i < PLACEHOLDER_ITEMS; i++) {
            items.add(new PlaceholderCell("grid-cell-" + i, I18n.get("pickupcard.grid.cell", i + 1)));
        }
        // 假指针归零：resize 会走 init() → 树/格子/u/调色板全重建，但那个坐标留在旧画布上。
        // 不清的话之后真实鼠标会被永久忽略，悬停指在旧位置（只有 dev 会碰到，但清了才自洽）。
        harnessPointerSet = false;
        grid = CardGridTree.build(items, this.width, this.height, u, palette);
    }

    /** GUI 倍数。设备像素对齐要用它（不交的话对齐会退化成"对齐到整数逻辑坐标"）。 */
    private float guiScale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        now = System.currentTimeMillis();
        // 【tick 不能漏】格子的悬停缓动来自组件基类（hoverAmount 问 nowNanos）；漏了它的症状是
        // "悬停硬切、没有缓动"，而且不报错（编辑场那边没调也看不出来，因为它的盒子无动画）。
        grid.tree().tick(now * 1_000_000L);
        CardGridTree.layout(grid, this.width, this.height, 1f / guiScale());
        grid.tree().pointerMove(harnessPointerSet ? harnessPointerX : mouseX,
                harnessPointerSet ? harnessPointerY : mouseY);
        TrellisColumn.syncHover(grid.tree());

        // 背板：与配置屏同一条（gui.fill(..., palette.backdrop)）。不画的话这一屏是透的 ——
        // 格子只盖住视口那一块，标题带与提示带是裸的，多人游戏里世界照跑、字读不出来。
        gui.fill(0, 0, this.width, this.height, palette.backdrop);

        try (NvgUi ui = NvgUi.begin(gui, palette, mouseX, mouseY, now)) {
            if (ui != null) {
                TrellisColumn.Frame surface = TrellisColumn.surface(ui.canvas(), palette,
                        new McGlyphPainter(ui), now, guiScale());
                // 【这一对 pushClip/popClip 不是可选的】Trellis 的 clipChildren 走的是
                // Canvas.clip → nvgIntersectScissor，它只管 NanoVG 那批形状；而格子的字是
                // 「先登记、close() 时统一交给原版批次」的（NvgUi 的延迟字形），登记时记的是
                // NvgUi 自己的裁剪框 —— 那个框只有 pushClip 会写。不包这一对的话：
                // 形状被 nvgScissor 裁掉了，**文字照旧画在绝对坐标上**，滚出视口的行会把
                // "卡 N"糊到标题和提示上（静止看对、滚一下才对不上）。
                // NvgUi 的类注释原话就是这条：「只设一套的症状是形状被裁了、文字糊在外面」。
                Rect view = grid.viewport();
                ui.pushClip(view.x(), view.y(), view.width(), view.height());
                try {
                    // ① 树：滚动容器（内容 + 每一格）。后画的盖前面的。
                    TrellisColumn.paint(surface, grid.tree());
                } finally {
                    ui.popClip();
                }
                // ② 标题与提示在裁剪框**之外**（它们不属于滚动区，不该跟着滚）
                Rect title = grid.titleBox().bounds();
                ui.text(I18n.get("pickupcard.grid.title"), title.x(), title.y(), palette.text);
                Rect hint = grid.hintBox().bounds();
                ui.textFitted(I18n.get("pickupcard.grid.hint", grid.metrics().cols(),
                                grid.metrics().rows(), Math.round(grid.cellWidth()),
                                Math.round(grid.cellHeight())),
                        hint.x(), hint.y(), palette.textDim, hint.width());
            }
        }
    }

    // ------------------------------------------------------------------
    // 输入
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        grid.tree().pointerDown((float) mouseX, (float) mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        grid.tree().pointerUp((float) mouseX, (float) mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 【传真指针，不传"列中心"】UiTree.scrollAt 本来就是"指哪滚哪"；配置屏为了保留旧手感
        // 用的是列中心，网格没有那份历史包袱。
        if (grid.tree().scrollAt((float) mouseX, (float) mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            // 【方向键一律吃掉】走到边界时不移动，但仍然算"被处理了"：放它漏下去的话，
            // 同一个方向键会先被这一屏"没做"，再落到 MC 的默认路径上（那看起来像失灵）。
            case GLFW.GLFW_KEY_LEFT:
                CardGridTree.navigate(grid, -1, 0);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                CardGridTree.navigate(grid, 1, 0);
                return true;
            case GLFW.GLFW_KEY_UP:
                CardGridTree.navigate(grid, 0, -1);
                return true;
            case GLFW.GLFW_KEY_DOWN:
                CardGridTree.navigate(grid, 0, 1);
                return true;
            default:
                break;
        }
        // 其余（含 Enter/Space 的控件激活）交给树 —— 键只发给焦点那一个。
        if (grid.tree().keyDown(keyCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // 【必须转发抬起】树的 heldKeys 账"先出账再派发"；不转发的话账永远出不去，
        // 于是同一个键的每一次按下都被当成 repeat（A-11b 那条）。
        boolean handled = grid.tree().keyUp(keyCode, modifiers);
        return handled || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------
    // harness 读数（几何全部从树读；与离线测试读的是同一批静态方法，于是两边同源）
    // ------------------------------------------------------------------

    /** 定妆读数。 */
    public String gridDump() {
        return grid == null ? "网格读数: 还没建树" : CardGridTree.dump(grid);
    }

    /** 自检：这一帧每一格都画过没有（"控件在、点得到、屏幕一块空白"是自绘界面的专属故障）。 */
    public String paintedDump() {
        if (grid == null) {
            return "网格自检: 还没建树";
        }
        int painted = 0;
        for (WidgetSlot slot : grid.cells()) {
            if (slot.widget().paintedIn(now)) {
                painted++;
            }
        }
        return "网格自检: " + painted + "/" + grid.cells().size() + " 个格子这一帧被画过";
    }

    /** 焦点位置读数。 */
    public String focusDump() {
        return grid == null ? "焦点=还没建树" : CardGridTree.focusDump(grid);
    }

    /** 焦点走出视口那条探针的读数。 */
    public String scrollProbeDump() {
        return grid == null ? "焦点进视口探针: 还没建树" : CardGridTree.scrollProbeDump(grid);
    }

    /** 滚动的命中读数。 */
    public String scrollHitDump() {
        return grid == null ? "网格滚动命中: 还没建树" : CardGridTree.scrollHitDump(grid);
    }

    /** harness 驱动：按一下方向键（走真事件路径，不是直接 requestFocus）。 */
    public void navForHarness(int keyCode) {
        keyPressed(keyCode, 0, 0);
    }

    /** harness 驱动：第 index 格的屏幕中心（几何从树读，宿主不另算一份）。 */
    public float[] cellCenterForHarness(int index) {
        Rect box = grid.cells().get(index).bounds();
        return new float[]{box.x() + box.width() / 2f, box.y() + box.height() / 2f};
    }

    /**
     * harness 读数：某一格的悬停缓动量（0 = 没悬停、1 = 完全悬停）。
     * <p>【为什么值得有一条读数】它同时验两件事：指针真的落在了那一格上；以及
     * {@code tree.tick(...)} 真的在跑 —— 漏掉 tick 时缓动量会<b>冻在 0</b>，
     * 而屏幕上只是"悬停硬切、没有缓动"，不报错（A-19 那条坑）。
     */
    public String hoverDump(int index) {
        if (grid == null || index < 0 || index >= grid.cells().size()) {
            return "悬停缓动: 无";
        }
        return String.format("悬停缓动: 第%d格=%.2f", index, grid.cells().get(index).hoverAmount());
    }

    /** harness 驱动：把指针定在某一格的中心上（MC 的真实鼠标挪不动，所以界面代记）。 */
    public void pointAtCellForHarness(int index) {
        float[] center = cellCenterForHarness(index);
        harnessPointerSet = true;
        harnessPointerX = center[0];
        harnessPointerY = center[1];
    }

    /**
     * harness 驱动：直接定到某个偏移。
     * <p>【为什么需要它】滚轮的步长是"一格 = 格高 + 缝"（本轮 184），而那恰好**躲开**了
     * "格子文字会糊到标题/提示上"的那一段偏移（约 89–123）—— 只按整格滚，那个 bug 拍不到。
     * 这条探针就是去拍它的（评审算出来的，见 {@code docs/plan.md} 的 A-19 遗留）。
     */
    public void scrollToForHarness(float offset) {
        grid.scroller().scrollTo(0f, offset);
    }

    /**
     * harness 驱动：滚一格。
     * <p>指针落在<b>视口正中</b>而不是某一格上 —— 步长会改变焦点之外的一切，而第一格可能
     * 已经被滚出去了（落在它上面事件就送不到容器）。
     */
    public void scrollForHarness(double delta) {
        Rect view = grid.viewport();
        mouseScrolled(view.x() + view.width() / 2f, view.y() + view.height() / 2f, delta);
    }
}
