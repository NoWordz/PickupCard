package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.HudSafeZone;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import dev.e33.trellis.ui.widget.Button;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import dev.e33.trellis.ui.WidgetSlot;
import com.niuqu.pickupcard.render.nvg.ui.McGlyphPainter;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.geom.Insets;
import dev.e33.trellis.layout.Align;
import dev.e33.trellis.layout.Justify;
import dev.e33.trellis.layout.Sizing;
import dev.e33.trellis.layout.Style;
import dev.e33.trellis.render.Canvas;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.Label;
import dev.e33.trellis.ui.UiEvent;
import dev.e33.trellis.ui.UiTree;
import dev.e33.trellis.text.TextAlign;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>整屏拖拽编辑场</b>：卡片停在哪，不靠滑条靠手 —— 按住那摞样例卡拖到想要的位置，
 * 「完成」存盘，Esc 取消。位置存成<b>画布分数</b>（0~1），换 GUI 缩放档不错位。
 *
 * <p>【为什么要整屏，而不是在预览列里拖】预览列只有屏幕的十几分之一大，拖三五个像素
 * 就是真屏上的一大段，拖不准；而且"位置好不好"是遮挡关系的问题 —— 真实 HUD、准星、
 * 侧栏都在 1:1 画布上，缩小的模拟屏看不出来。与 keyhud 的拖拽编辑同一套路。
 *
 * <p>【所见即所得（2026-09-19 方案一，审计第三问的定案）】从前编辑场自己算一套几何：
 * 区域框夹在"离右缘一个占地宽"里，游戏里又夹在"离右缘一个卡宽"里，存进配置的锚点却
 * 两边都不管 —— 三层各夹各的，拖到头"堆不动、设了没反应"。现在样例堆直接走
 * {@link StackLayout#stack}（与游戏同一条公式、同一个夹取）：堆停下的地方就是游戏里
 * 会画的地方；锚线单独画出来 —— 被边距夹住时锚线还在动，"拖了没反应"不再是谜，
 * 界面上明说「卡已贴边距」。
 *
 * <p>【抓取以卡为基准】按住的是卡，偏移就记"手与卡的相对位置"（从前记的是手与锚线的，
 * 卡被夹住时离锚线有一段距离，拖动要先吃掉这段死区卡才动）。
 *
 * <p>【真卡为什么要挂起】屏幕上只能有一摞卡。编辑场自己画一摞样例（当前配置的样子），
 * 真卡若照画就叠在一起分不清谁是谁；{@link CardStage#setSuspended} 只是"这几帧不画"，
 * 背后的账本照旧 —— 取消退出时玩家的提示一条不少。
 */
public final class AnchorEditScreen extends Screen {

    private final Screen parent;
    private final PickupCardConfig.Values v = PickupCardConfig.VALUES;

    /** 进编辑场那一刻的值（Esc 取消的"原样"就是它 —— 其实什么都没写，回到父界面即取消）。 */
    private final float origX;
    private final float origY;
    /** 正在编辑的锚点（画布分数；-1 = 自动）。拖动会把它变成明确的分数。 */
    private float anchorX;
    private float anchorY;

    private boolean dragging;
    /** 手与<b>最新那张卡</b>左上角的偏移（抓取以卡为基准 —— 见类注释）。 */
    private double grabDx;
    private double grabDy;
    /** 抓住那张卡的宽（右缘对齐时锚线 = 卡右缘，要用它折算）。 */
    private float grabW;

    private long now;
    private Button saveButton;
    private Button resetButton;

    private NvgPalette palette;
    private final NvgCardPainter painter = new NvgCardPainter();

    /** 编辑场的树（A-17）：蒙层 / 两行字 / 底部按钮行 —— 几何与命中都从这里出去。 */
    private UiTree tree;
    /** 蒙层那一格：全屏、可拖（在这块屏上按哪儿都算抓那摞卡）。 */
    private DragSurface backdrop;
    /** 两行字（标题 / 提示）—— 由 {@code Label} 自己经字形缝画，几何从树读。 */
    private Label titleBox;
    private Label hintBox;

    /** 进编辑场时那两颗按钮的样子（树里的槽托着它们，几何归树）。 */
    private WidgetSlot saveSlot;
    private WidgetSlot resetSlot;

    /** 这棵树布局过没有 —— {@code bounds()} 在布局之前读会 NPE（见 {@link #treeDump()}）。 */
    private boolean treeLaidOut;

    public AnchorEditScreen(Screen parent) {
        super(net.minecraft.network.chat.Component.translatable("pickupcard.anchor.title"));
        this.parent = parent;
        LayoutSettings current = PickupCardConfig.layoutSnapshot();
        this.origX = current.anchorX();
        this.origY = current.anchorY();
        this.anchorX = origX;
        this.anchorY = origY;
    }

    @Override
    protected void init() {
        // 真卡挂起：屏幕上只能有一摞卡（见类注释）
        PickupCard.LOGGER.info("[编辑场] init");
        CardStage.INSTANCE.setSuspended(true);
        palette = NvgPalette.of(CardStage.INSTANCE.previewStyle(),
                dev.e33.trellis.tokens.Units.u(this.height));
        saveButton = new Button(I18n.get("pickupcard.anchor.done"), () -> I18n.get("pickupcard.anchor.done"),
                this::saveAndClose);
        resetButton = new Button(I18n.get("pickupcard.anchor.reset"),
                () -> I18n.get(isAuto() ? "pickupcard.anchor.resetDone" : "pickupcard.anchor.reset"), this::resetToAuto);
        buildTree();
    }

    /**
     * 建编辑场的树（A-17）。
     *
     * <p>【树里有什么】蒙层（全屏 + 可拖，抓哪儿都算抓那摞卡）、两行字的盒子、底部两颗按钮。
     * <b>括号与卡堆不在树里</b>：卡堆要画 MC 物品与位图（框架画布没这两样）、括号要摆在业务
     * 算出来的任意坐标（L1 没有绝对定位）—— 两条缺口都记在 {@code docs/plan.md} 的 A-17。
     * 它们仍由宿主画，但"画在树的哪一层"由 {@link #render} 的顺序定死。
     *
     * <p>【为什么蒙层排在第一个】树序 = 绘制顺序（后画的盖前面的）：蒙层先画，所以它盖住游戏
     * 画面、但不盖后面的兄弟。命中是反的（从最后一个子节点往前找），所以点按钮时按钮收、
     * 点空白处才落到蒙层上变成一次拖拽 —— 一条布局规矩同时管住了这两件事。
     *
     * <p>【几何与改动前逐位相同】标题 (8,6)、提示 (8,17)、按钮行 120+8+120 居中、
     * 底边留 8 —— 这一版只是把这几笔从 {@code init()} 里的手算搬进树的 padding/gap/grow，
     * 没有一个数字被顺手改过（改观感要单独一轮，不能混在重构里）。
     */
    private void buildTree() {
        int bw = 120;
        int bh = 18;
        int gap = 8;
        backdrop = new DragSurface(Style.column()
                .withPadding(Insets.of(6f, 8f, 8f, 8f))
                .withGap(2f)
                .withAlign(Align.STRETCH));
        titleBox = backdrop.add(new Label(this.title.getString(),
                        Style.column().withHeight(Sizing.fixed(9f)))
                .color(0xFFFFFFFF)
                .verticalAlign(TextAlign.V.TOP));
        // 提示文案每帧现取：锚点可拖，anchorX / anchorY 会变 —— 用取值口就不用重建树。
        // 【参数必须交给 I18n.get，不要自己套一层 String.format】1.20.1 的 `I18n.get(key)`
        // 就算一个参数都不给也会执行一遍 `String.format` —— 值里带 `%.2f` 时当场抛，它 catch
        // 之后返回的是 **"Format error: 原文"**（源码：`I18n.get`）。把参数交进去，
        // 格式这一步才在它手里做对。这是 A-17 顺手修过的既有 bug。
        hintBox = backdrop.add(new Label(
                        () -> isAuto() ? I18n.get("pickupcard.anchor.autoHint")
                                : I18n.get("pickupcard.anchor.customHint", anchorX, anchorY),
                        Style.column().withHeight(Sizing.fixed(9f)))
                .color(palette.textDim)
                .fitted(true)
                .verticalAlign(TextAlign.V.TOP));
        // 撑开中间那段：于是按钮行被顶到底边（等价于从前那个 by = height - bh - 8）
        backdrop.add(new Box(Style.column().withGrow(1f)));
        Component buttonRow = backdrop.add(new Box(Style.row()
                .withJustify(Justify.CENTER).withGap(gap).withHeight(Sizing.fixed(bh))));
        saveSlot = buttonRow.add(new WidgetSlot(saveButton,
                Style.row().withWidth(Sizing.fixed(bw)).withHeight(Sizing.fixed(bh)), palette));
        resetSlot = buttonRow.add(new WidgetSlot(resetButton,
                Style.row().withWidth(Sizing.fixed(bw)).withHeight(Sizing.fixed(bh)), palette));
        tree = new UiTree(backdrop);
    }

    /** 只占位、自己不画东西的盒子（撑开的 spacer 与按钮行；两行字已改用 {@code Label}）。 */
    private static final class Box extends Component {
        Box(Style style) {
            style(style);
            // 【为什么关掉状态叠加层】基类那层白色覆盖是给"有底色的控件"做反馈的；这些盒子
            // 自己不画任何东西，叠上去就是**凭空多一层白雾**：指针停在中间那段（撑开的 spacer）
            // 整条刷白，按住更是 24% 白。A-17 起它们在树里 —— 这是本轮新引入的观感变化（评审逮到的）。
            stateOverlay(false);
        }

        @Override
        protected void drawContent(Canvas canvas) {
        }
    }

    /**
     * 蒙层那一格：<b>画半透明底 + 接住拖拽</b>。
     *
     * <p>【为什么拖拽挂在它身上】这一屏的"点哪都行"是编辑场的手感（用户 2026-09-19 拍板）：
     * 全屏的那一格是这趟拖拽的所有者，抓取偏移记的是"手与最新那张卡的相对位置"，
     * 所以卡不会跳到指针底下。它<b>不碰</b> POINTER_DOWN / POINTER_UP —— 那是发给最深的
     * 命中目标的（按钮要它）；拖拽有自己的三个事件，而"这一下按的算不算拖"由树判定。
     */
    private final class DragSurface extends Component {

        DragSurface(Style style) {
            style(style);
            draggable(true);
            // 它自己画薄暮色，不要基类那层白色叠加（按在边缘时那层会铺满整屏）
            stateOverlay(false);
        }

        @Override
        protected void drawContent(Canvas canvas) {
            // 薄暮色：括号和字读得出，遮挡关系还看得清（值与改动前那笔 gui.fill 相同）
            canvas.fillRect(bounds(), 0x59000000);
        }

        /**
         * 只管拖拽那三个事件，<b>POINTER_DOWN / POINTER_UP 一概不接</b>。
         *
         * <p>【为什么】这两个是发给最深的命中目标的，而这一格是根 —— 在<b>捕获阶段</b>把它们
         * 吃掉，等于让整屏所有子控件都收不到按下：A-17 第一版就是这么把两颗按钮点死的
         * （按钮有焦点环、看着像活的，就是什么都不做；评审从代码推出来的）。
         * 拖拽改用 {@link UiEvent.Type#DRAG_START} 之后，它有自己的成套事件，不必再借别人的按下。
         */
        @Override
        protected boolean onEvent(UiEvent event) {
            switch (event.type()) {
                case DRAG_START:
                    // 抓取偏移 = 手抓住的是最新那张卡的哪个位置（抓取以卡为基准 —— 见类注释）。
                    // 按下的那一点由框架给（DRAG_START 的坐标就是它），不必去蹭 POINTER_DOWN。
                    CardSlot newest = sampleSlots().get(0);
                    grabDx = event.x() - newest.x();
                    grabDy = event.y() - newest.y();
                    grabW = newest.width();
                    dragging = true;
                    return true;
                case DRAG:
                    setAnchorFromCard(event.x() - grabDx, event.y() - grabDy);
                    return true;
                case DRAG_END:
                    dragging = false;
                    return true;
                default:
                    return false;
            }
        }
    }

    /** 宿主的 GUI 倍数：Trellis 接画布要对齐到设备像素，要把它交进去（见 {@code NvgCanvas.attach}）。 */
    private float guiScale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    @Override
    public void removed() {
        // 挂起必须解除，否则编辑场一关真卡永远不画（这条日志是"关掉有据"，不是诊断）
        PickupCard.LOGGER.info("[编辑场] 关闭，恢复真卡渲染");
        CardStage.INSTANCE.setSuspended(false);
        super.removed();
    }

    // ------------------------------------------------------------------
    // 几何：编辑中的锚点（与游戏同一套公式、同一个夹取）
    // ------------------------------------------------------------------

    /** 编辑中的布局快照：展开/消失/对齐/间距/缩放照当前配置，锚点用编辑中的值。 */
    private LayoutSettings editing() {
        LayoutSettings live = PickupCardConfig.layoutSnapshot();
        return new LayoutSettings(live.appearMode(), live.exitMode(), live.align(),
                v.separation.get().floatValue(), v.scalePercent.get(), anchorX, anchorY).sanitized();
    }

    private boolean isAuto() {
        return anchorX < 0f && anchorY < 0f;
    }

    private float cardScale() {
        return PreviewStage.scale();
    }

    /** 编辑场画布：与配置界面预览同一条构造路（settings/layout 取当前生效值）。 */
    private CardCanvas canvas(StyleModel style, float scale) {
        return new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                        style.bumpEnabled()),
                style, PickupCardConfig.snapshot(), PickupCardConfig.layoutSnapshot(),
                this.width, this.height, scale);
    }

    /**
     * 样例堆的槽位：<b>与游戏完全同一条排布</b> —— 同一公式、同一个"卡不出边距"夹取
     * （所见即所得）。堆最多画五张示意（画满同屏上限只会变成一堵墙，括号已经把
     * 最大占地说清楚了）。
     */
    private List<CardSlot> sampleSlots() {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        float scale = cardScale();
        CardCanvas canvas = canvas(style, scale);
        var font = Minecraft.getInstance().font;
        LayoutSettings lay = editing();
        PreviewStage.Sample[] samples = PreviewStage.Sample.values();
        int count = Math.min(5, Math.max(1, v.maxOnScreen.get()));
        float cardH = CardMetrics.height(canvas, font);
        List<CardView> views = new ArrayList<>(count);
        List<StackLayout.Size> sizes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CardView view = PreviewStage.settledView(samples[i % samples.length]);
            views.add(view);
            sizes.add(new StackLayout.Size(CardMetrics.width(canvas, font, view), cardH));
        }
        // 游戏侧的实参一字不差：宽度上限夹取、底部留白、卡间距全同源
        List<StackLayout.Slot> slots = StackLayout.stack(sizes, this.width, this.height, lay,
                CardStage.MARGIN_X, HudSafeZone.bottomInset(), lay.separation());
        List<CardSlot> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StackLayout.Slot s = slots.get(i);
            out.add(new CardSlot(views.get(i), s.x(), s.y(), s.width(), s.height()));
        }
        return out;
    }

    /**
     * 最大占地的括号：按"最宽样例 × 同屏上限"跑一遍同一套排布，取并集 ——
     * 框因此天然被同一个边距夹住，不再有自己的那套夹取。
     */
    private record Bracket(float x, float y, float w, float h, boolean clamped) {
    }

    private Bracket bracket() {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        float scale = cardScale();
        CardCanvas canvas = canvas(style, scale);
        var font = Minecraft.getInstance().font;
        LayoutSettings lay = editing();
        float cardH = CardMetrics.height(canvas, font);
        float widest = 0f;
        for (PreviewStage.Sample s : PreviewStage.Sample.values()) {
            widest = Math.max(widest, CardMetrics.naturalWidth(canvas, font, PreviewStage.settledView(s)));
        }
        widest *= scale;
        int rows = Math.max(1, v.maxOnScreen.get());
        List<StackLayout.Size> sizes = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            sizes.add(new StackLayout.Size(widest, cardH));
        }
        List<StackLayout.Slot> slots = StackLayout.stack(sizes, this.width, this.height, lay,
                CardStage.MARGIN_X, HudSafeZone.bottomInset(), lay.separation());
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (StackLayout.Slot s : slots) {
            minX = Math.min(minX, s.x());
            minY = Math.min(minY, s.y());
            maxX = Math.max(maxX, s.x() + s.width());
            maxY = Math.max(maxY, s.y() + s.height());
        }
        // 贴边判定：最新那张"想停"的 x（意图）与实际被夹到的 x 对不上 = 贴边了
        float intent = lay.align() == LayoutSettings.Side.RIGHT
                ? lay.anchorLeft(this.width) - widest
                : lay.anchorLeft(this.width);
        boolean clamped = Math.abs(slots.get(0).x() - intent) > 0.5f;
        return new Bracket(minX, minY, Math.max(1f, maxX - minX), Math.max(1f, maxY - minY), clamped);
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        now = System.currentTimeMillis();
        // 【树先算完再画】几何、悬停、拖拽目标全在树上；指针每帧推给它一次（MC 每帧都调 render），
        // 拖拽因此是"每帧按当前位置更新"，而不是靠 mouseDragged 那一串事件。
        tree.layout(new Rect(0f, 0f, this.width, this.height), 1f / guiScale());
        treeLaidOut = true;
        // 【tick 必须每帧喂】悬停缓动量的推进（hoverEase.at）读树里这份时刻 ——
        // 从前没喂，缓动冻在 0，编辑场按钮的悬停反馈整体消失（2026-09-25 真机）。
        tree.tick(now * 1_000_000L);
        tree.pointerMove(mouseX, mouseY);
        TrellisColumn.syncHover(tree);

        Bracket b = bracket();
        try (NvgUi ui = NvgUi.begin(gui, palette, mouseX, mouseY, now)) {
            if (ui != null) {
                TrellisColumn.Frame surface = TrellisColumn.surface(ui.canvas(), palette,
                        new McGlyphPainter(ui), guiScale());
                // ① 树：蒙层 → 两行字的盒子 → 按钮行（后画的盖前面的）
                TrellisColumn.paint(surface, tree);
                // ② 宿主 decor：括号与锚线（任意坐标、业务算的 → 见 buildTree 的说明）
                drawBrackets(ui, b);
                // ③ 两行字在树里（两个 Label，自己经字形缝落笔）—— 从前这两笔在树外由本屏画。
                //    右下角那行控件说明仍是宿主画：它是绝对坐标（width - 8 / height - 12），
                //    不在任何盒子里。
                ui.textRight(I18n.get("pickupcard.anchor.controls"), this.width - 8f, this.height - 12f, palette.textDim);
            }
        }
        paintSampleStack(gui);
    }

    /** 区域括号 + 锚线。框是"承诺"，卡是"样子"，锚线是"意图" —— 三样说的都是同一套几何。 */
    private void drawBrackets(NvgUi ui, Bracket b) {
        float len = 10f;
        float t = 2f;
        int color = ui.palette.accent;
        float x = b.x();
        float y = b.y();
        float x2 = x + b.w();
        float y2 = y + b.h();
        // 四个角，每个角两条短线
        ui.fillRoundRect(x, y, len, t, 1f, color);
        ui.fillRoundRect(x, y, t, len, 1f, color);
        ui.fillRoundRect(x2 - len, y, len, t, 1f, color);
        ui.fillRoundRect(x2 - t, y, t, len, 1f, color);
        ui.fillRoundRect(x, y2 - t, len, t, 1f, color);
        ui.fillRoundRect(x, y2 - len, t, len, 1f, color);
        ui.fillRoundRect(x2 - len, y2 - t, len, t, 1f, color);
        ui.fillRoundRect(x2 - t, y2 - len, t, len, 1f, color);
        // 【为什么写"示意"】框宽按<b>样例</b>的最宽算 —— 玩家真捡到更长的名字时卡会更宽。
        ui.text(I18n.get("pickupcard.anchor.footprint"), x, y - 11f, ui.palette.textDim);
        // 锚线：意图的那条竖线。堆被边距夹住时它还在动 —— "拖了没反应"从这里变成"看得见的让位"
        float line = editing().anchorLeft(this.width);
        ui.fillRoundRect(line - 0.75f, y - 8f, 1.5f, (y2 + 8f) - (y - 8f), 0.75f,
                NvgUi.fade(color, 0.45f));
        if (b.clamped()) {
            ui.textFitted(I18n.get("pickupcard.anchor.clamped"), x, y2 + 4f, ui.palette.textDim,
                    this.width - x - 8f);
        }
    }

    /**
     * 编辑场里的样例堆：与配置界面预览同一套样例、同一个画笔、同一个缩放 ——
     * 编辑场里看到的多宽多高，游戏里就是多宽多高。
     * <p>【贴着容量画】游戏里放不下的卡根本不会上屏（几何容量门把它们退回排队），
     * 编辑场照办：容量之外的示意卡不画 —— 这也是"所见即所得"的一半。
     */
    private void paintSampleStack(GuiGraphics gui) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        float scale = cardScale();
        LayoutSettings lay = editing();
        float unscaledH = style.boxHeight();
        int capacity = StackLayout.fittingCount(
                lay.anchorTop(this.height, unscaledH, HudSafeZone.bottomInset()),
                unscaledH, lay.separation());
        List<CardSlot> all = sampleSlots();
        List<CardSlot> slots = all.subList(0, Math.min(all.size(), Math.max(0, capacity)));
        if (!slots.isEmpty()) {
            painter.paint(gui, canvas(style, scale), slots);
        }
    }

    // ------------------------------------------------------------------
    // 输入：拖（点哪都行，偏移保住"不跳"）、完成/取消
    // ------------------------------------------------------------------

    /**
     * 按下：<b>先问树</b>（按钮那一格会收下，空白处落到蒙层上变成拖拽），没人吃才还给 MC。
     *
     * <p>【为什么"先问树"而不是"自己判命中"】命中只有一份真相：树按 {@code bounds()} 取，
     * 而绘制读的是同一个矩形（判据 1）。宿主再自己 {@code box.contains(...)} 一次就是第二份。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tree.pointerDown((float) mouseX, (float) mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 拖动：<b>把指针位置推给树</b>，让树的拖拽（捕获在蒙层那一格上）自己算新锚点。
     *
     * <p>【为什么这里也推一次，render 里还推】真玩家那条路每帧都有 render，推一次就够；
     * 但 harness 的 {@code dragForHarness} 是"按下→拖→松开"一口气调完的（中间没有帧），
     * 只靠 render 的话那一次拖动根本不发生。推的是同一个位置，重复推没有副作用。
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        tree.pointerMove((float) mouseX, (float) mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        tree.pointerUp((float) mouseX, (float) mouseY);
        return true;
    }

    /**
     * 键盘：<b>键先问树</b>（焦点在按钮上时 Enter / Space = 按它一下），Esc 与"没焦点时的回车"归宿主。
     *
     * <p>【为什么 Esc 不进树】关界面是 {@code Screen} 级的事（原版也在这里拦），
     * 与"焦点在哪个控件上"无关 —— 放进树等于让焦点决定能不能退出。
     *
     * <p>【为什么回车要留一手】编辑场的老手感是"回车=存盘"。焦点在按钮上时由按钮接过去
     * （标准做法，也是能看见的那件事）；焦点不在任何按钮上时（刚进来还没点过）走原来那条路。
     * 所以"点了「回到默认」再回车"会变成再按一次那颗钮，而不是存盘 —— 这是键盘路由的代价，
     * 记在这里，免得下一个人以为是 bug。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        if (tree != null && tree.keyDown(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 键抬起：<b>必须转发</b>（A-11b 在配置屏还掉的那笔债，编辑场这一版也要还）。
     *
     * <p>不转发的话树上"这个键还按着"的账永远出不去 —— 编辑场里按 Enter 激活按钮之后就换屏了，
     * 今天看不出后果，但同一棵树上的 {@code repeatsDeduced()} 会读出虚高的数（读数一脏，
     * 下一次真机验收就骗人）。评审指出的正是这一条。
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (tree != null && tree.keyUp(keyCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        cancel();       // Esc 走这里：取消，不写配置
    }

    /**
     * 把锚点设到"让最新那张卡的边贴到这里"。以<b>卡</b>为基准而不是锚线：抓着卡拖，
     * 卡就该跟着手走（被边距夹住时除外 —— 那是游戏里真实会发生的事，锚线会替它走）。
     * <b>全屏幕随便拖（2026-09-19 用户拍板）</b>：锚点本身没有任何自造的夹取圈，
     * 压到 HUD 带上也照存；放得下几张由游戏侧的几何容量自己少排。
     */
    private void setAnchorFromCard(double cardLeft, double cardTop) {
        float lineX = (float) cardLeft
                + (editing().align() == LayoutSettings.Side.RIGHT ? grabW : 0f);
        setAnchorPx(lineX, cardTop);
    }

    private void setAnchorPx(double x, double y) {
        float px = (float) Math.max(0, Math.min(x, this.width));
        float py = (float) Math.max(0, Math.min(y, this.height));
        anchorX = px / this.width;
        anchorY = py / this.height;
    }

    /** 「回到默认」：立刻回到自动锚点（还不写盘 —— 完成/取消才定生死）。 */
    private void resetToAuto() {
        anchorX = LayoutSettings.AUTO_ANCHOR;
        anchorY = LayoutSettings.AUTO_ANCHOR;
    }

    /** 「完成」：写配置（分数保留三位小数足够 —— 256 宽的画布上 0.001 就是四分之一像素）。 */
    private void saveAndClose() {
        v.anchorX.set((double) Math.round(anchorX * 1000f) / 1000.0);
        v.anchorY.set((double) Math.round(anchorY * 1000f) / 1000.0);
        ConfigPageSpec.changed();
        Minecraft.getInstance().setScreen(parent);
    }

    /** Esc：不写配置，直接回父界面。 */
    private void cancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ------------------------------------------------------------------
    // 给 dev harness 的只读/驱动入口
    // ------------------------------------------------------------------

    /**
     * 给 harness 用：<b>树这一侧的几何读数</b>（A-17）。
     *
     * <p>【为什么另起一行，不并进 {@link #stateDump()}】{@code stateDump} 是 A-16 之前就在的
     * 读数，第 38 轮有它逐字的一份 —— 迁树之后要比的是"老读数一个字没变 + 树确实在管事"。
     * 把新字段并进去，那份逐字比对就没法做了。
     *
     * <p>标题 / 提示 / 两颗按钮的盒子<b>全部从树读</b>：迁树之前它们是 {@code init()} 里手算的
     * 两个 {@code Rect}，读数里没有它们的身影（所以那时也证明不了"画的和命中的是同一份"）。
     */
    public String treeDump() {
        if (tree == null || !treeLaidOut) {
            // 【为什么要判"布局过没有"】bounds() 读的是布局算出来的那个矩形，一帧都没渲染过时
            // 节点还是 null（`Component.bounds()` 当场 NPE）。窗口最小化时 MC 会跳过渲染，
            // 而 harness 照样会调这个读数 —— 那种情况下不该是一个 NPE（评审逮到的）。
            return tree == null ? "(树还没建)" : "(树还没布局)";
        }
        Rect t = titleBox.bounds();
        Rect h = hintBox.bounds();
        Rect s = saveSlot.bounds();
        Rect r = resetSlot.bounds();
        return String.format(java.util.Locale.ROOT,
                "树=建好 拖拽中=%s 标题=(%.0f,%.0f) 提示=(%.0f,%.0f) "
                        + "完成钮=(%.0f,%.0f,%.0fx%.0f) 重置钮=(%.0f,%.0f,%.0fx%.0f)",
                dragging, t.x(), t.y(), h.x(), h.y(),
                s.x(), s.y(), s.width(), s.height(), r.x(), r.y(), r.width(), r.height());
    }

    public String stateDump() {
        Bracket b = bracket();
        return String.format(java.util.Locale.ROOT,
                "编辑场 锚点=(%.3f,%.3f)%s 占地=%.0fx%.0f@(%d,%d)%s 真卡挂起=%s",
                anchorX, anchorY, isAuto() ? "（自动）" : "", b.w(), b.h(),
                Math.round(b.x()), Math.round(b.y()), b.clamped() ? " 已贴边距" : "",
                CardStage.INSTANCE.suspended());
    }

    /** 走真实事件路径拖一次：按在最新那张卡上（偏移=0）→ 拖到目标分数 → 松开。 */
    public boolean dragForHarness(double fx, double fy) {
        CardSlot newest = sampleSlots().get(0);
        mouseClicked(newest.x(), newest.y(), 0);
        mouseDragged(fx * this.width, fy * this.height, 0, 0, 0);
        mouseReleased(fx * this.width, fy * this.height, 0);
        return true;
    }

    /**
     * 给 harness 用：<b>点一下「回到默认」那颗钮</b>（按下 + 抬起都走真事件路径，
     * 落点是树给的那一格的中心）。
     *
     * <p>【为什么必须有这一条】A-17 第一版的蒙层在<b>捕获阶段</b>把 POINTER_DOWN 吃掉了，
     * 两颗按钮全点不动 —— 而当时的 harness 只驱动过"拖"和"取消"，**谁都没点过按钮**，
     * 所以三轮真机全绿（评审从代码推出来的，不是真机看出来的）。这一条补上那个缺口：
     * 它红 = 按钮又死了。读数里"锚点变回（自动）"就是钮活着的证据。
     */
    public String clickResetForHarness() {
        Rect box = resetSlot.bounds();
        float cx = box.x() + box.width() / 2f;
        float cy = box.y() + box.height() / 2f;
        mouseClicked(cx, cy, 0);
        mouseReleased(cx, cy, 0);
        return stateDump();
    }

    public boolean saveForHarness() {
        saveAndClose();
        return true;
    }

    public boolean cancelForHarness() {
        cancel();
        return true;
    }
}
