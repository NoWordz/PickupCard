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
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.McGlyphPainter;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import dev.e33.trellis.geom.Rect;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
    private NvgButton saveButton;
    private NvgButton resetButton;
    private List<NvgWidget> buttons = List.of();

    /** 两颗按钮的格子（见 {@link #boxOf}）；进编辑场时按画布算一次。 */
    private Rect saveBox = new Rect(0f, 0f, 0f, 0f);
    private Rect resetBox = new Rect(0f, 0f, 0f, 0f);
    private NvgPalette palette;
    private final NvgCardPainter painter = new NvgCardPainter();

    public AnchorEditScreen(Screen parent) {
        super(Component.translatable("pickupcard.anchor.title"));
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
        palette = NvgPalette.of(CardStage.INSTANCE.previewStyle());
        int bw = 120;
        int bh = 18;
        int gap = 8;
        int totalW = bw * 2 + gap;
        int by = this.height - bh - 8;
        saveButton = new NvgButton(I18n.get("pickupcard.anchor.done"), () -> I18n.get("pickupcard.anchor.done"),
                this::saveAndClose);
        saveBox = new Rect(this.width / 2f - totalW / 2f, by, bw, bh);
        resetButton = new NvgButton(I18n.get("pickupcard.anchor.reset"),
                () -> I18n.get(isAuto() ? "pickupcard.anchor.resetDone" : "pickupcard.anchor.reset"), this::resetToAuto);
        resetBox = new Rect(this.width / 2f + totalW / 2f - bw, by, bw, bh);
        buttons = List.of(saveButton, resetButton);
    }

    /**
     * 某个按钮的格子：<b>命中、悬停、绘制、拖拽都用它</b> —— A-10 第二步起控件不存几何，
     * 谁用它谁交一份（这里的两颗按钮是"树外控件"，几何由这个界面自己排）。
     */
    private Rect boxOf(NvgWidget widget) {
        return widget == resetButton ? resetBox : saveBox;
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
        // 世界之上盖一层薄暮色：括号和字读得出，遮挡关系还看得清
        gui.fill(0, 0, this.width, this.height, 0x59000000);

        Bracket b = bracket();
        try (NvgUi ui = NvgUi.begin(gui, palette, mouseX, mouseY, now)) {
            if (ui != null) {
                ui.text(this.title.getString(), 8f, 6f, 0xFFFFFFFF);
                ui.textFitted(isAuto() ? I18n.get("pickupcard.anchor.autoHint")
                                : String.format(java.util.Locale.ROOT, I18n.get("pickupcard.anchor.customHint"),
                                anchorX, anchorY),
                        8f, 17f, palette.textDim, this.width - 16f);
                drawBrackets(ui, b);
                TrellisColumn.Frame surface = TrellisColumn.surface(ui.canvas(), palette,
                        new McGlyphPainter(ui), now, guiScale());
                for (NvgWidget w : buttons) {
                    Rect box = boxOf(w);
                    w.hover(box.contains((float) mouseX, (float) mouseY));
                    w.draw(surface.ctxFor(box));
                }
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (NvgWidget w : buttons) {
            Rect box = boxOf(w);
            if (box.contains((float) mouseX, (float) mouseY)
                    && w.press(box, mouseX, mouseY, button)) {
                return true;
            }
        }
        // 点空白处即开始拖：偏移记的是"手与卡的相对位置"，卡不会跳到指针底下
        dragging = true;
        CardSlot newest = sampleSlots().get(0);
        grabDx = mouseX - newest.x();
        grabDy = mouseY - newest.y();
        grabW = newest.width();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (NvgWidget w : buttons) {
            w.drag(boxOf(w), mouseX, mouseY);
        }
        if (dragging) {
            setAnchorFromCard(mouseX - grabDx, mouseY - grabDy);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (NvgWidget w : buttons) {
            Rect box = boxOf(w);
            w.release(box, mouseX, mouseY, box.contains((float) mouseX, (float) mouseY));
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    public boolean saveForHarness() {
        saveAndClose();
        return true;
    }

    public boolean cancelForHarness() {
        cancel();
        return true;
    }
}
