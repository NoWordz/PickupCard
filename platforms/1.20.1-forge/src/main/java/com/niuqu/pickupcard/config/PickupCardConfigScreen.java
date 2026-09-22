package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.HudSafeZone;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgScroll;
import com.niuqu.pickupcard.render.nvg.ui.ScrollMath;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import com.niuqu.pickupcard.render.nvg.ui.TrellisBridge;
import com.niuqu.pickupcard.render.nvg.ui.McFont;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.geom.Snapping;
import dev.e33.trellis.text.FontStack;
import dev.e33.trellis.text.TextLayout;
import dev.e33.trellis.text.TextMeasurer;
import dev.e33.trellis.ui.UiTree;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.McGlyphPainter;
import com.niuqu.pickupcard.render.nvg.ui.PaintCtx;
import com.niuqu.pickupcard.render.nvg.ui.Tween;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 游戏内配置界面：<b>左侧一列分类，右侧实时预览 + 选项行</b>（同一行里左边标签、右边控件）。
 *
 * <p>【这个类还剩什么（2026-09-19 架构审计后的分工）】从前 1700 多行、身兼十二职：配置项
 * 排布、控件绑定、恢复默认、预览管线、过滤列表、帧统计、harness API 全在这一间屋里，
 * 结果"每个功能互相打架"——页面归属漂了、默认值抄错了、预览卡片叠在一起。现在它只留
 * <b>界面壳</b>：页面状态、行摆位、绘制、事件分发、harness 只读入口。其余各归各的类：
 * <ul>
 *   <li>哪个配置项归哪页、控件怎么造、默认值是什么 → {@link ConfigPageSpec}（单一出处）；</li>
 *   <li>三张过滤名单（可增删的动态行）→ {@link FilterPageBuilder}；</li>
 *   <li>预览舞台与样例卡（含每张卡的唯一身份）→ {@link PreviewStage}。</li>
 * </ul>
 *
 * <p>【界面自己不存值】控件的读写直接打到 Forge 配置上；否则"界面显示 5、实际画 9"迟早出现。
 * <p>【换页没有动画（2026-09-19 用户拍板）】切页就是瞬间换内容，留下来的动画只有悬停与
 * 强调条这两种"指哪是哪"的反馈。
 */
public final class PickupCardConfigScreen extends Screen {

    // ---- 界面尺度 ----
    private static final int PAD = 6;
    /** 底部说明被截断时补在末尾的省略号。 */
    private static final String ELLIPSIS = "…";
    /** 全界面统一的行距/行高：放不下就滚，节奏不随内容变。 */
    /**
     * 短动画的时长（毫秒）：只在"我按了东西"的那一瞬间回答问题。超过 200ms 它就开始和
     * 玩家的下一次操作抢时间；短于 80ms 就等于没有。
     */
    private static final long HOVER_IN_MS = 110L;
    private static final long HOVER_OUT_MS = 150L;
    private static final long TAB_MS = 160L;
    /** 「加一条」被拒时那句话在底部停留多久（够读完，又不会把悬停说明永久顶掉）。 */
    private static final long FILTER_NOTE_MS = 6_000L;

    /** 给 harness 看的列几何（只读）：三列到底摆在哪、预览收没收起。 */
    public String columnDump() {
        ConfigLayout lo = layout();
        return String.format(java.util.Locale.ROOT,
                "画布 %dx%d 缩放%.0f | tabs=(x%.0f w%.0f h%.0f%s) items=(x%.0f w%.0f h%.0f) preview=%s%s 行数=%d 可见行=%d 偏移=%.0f",
                this.width, this.height, Minecraft.getInstance().getWindow().getGuiScale(),
                lo.tabs().x(), lo.tabs().w(), lo.tabs().h(), lo.tabsOnTop() ? " 顶排" : "",
                lo.items().x(), lo.items().w(), lo.items().h(),
                lo.previewVisible() ? String.format(java.util.Locale.ROOT, "w%.0f", lo.preview().w())
                        : "收起",
                lo.previewVisible() ? (lo.switcherVisible() ? " 切样例行" : " 无切样例行") : "",
                rowsModel.all().size(),
                Math.max(1, (int) (lo.items().h() / ConfigRows.ROW_STEP)),
                itemsScroll == null ? 0f : itemsScroll.offset());
    }

    /** 给 harness 用：让配置项那一列滚一格（走的是和真人滚轮同一条路）。 */
    public boolean scrollForHarness(double delta) {
        return mouseScrolled(this.width / 2.0, this.height / 2.0, delta);
    }

    /** 给 harness 用：当前滚动偏移。 */
    public float scrollOffsetForHarness() {
        return itemsScroll == null ? 0f : itemsScroll.offset();
    }

    /** 给 harness 用：现在是哪一页、预览是哪个样例（换页/换样例的验证要能读出来）。 */
    public String stateDump() {
        ConfigLayout lo = layout();
        ConfigLayout.Rect card = lo.previewCard();
        float hover = 0f;
        for (ConfigRows.Row row : rowsModel.all()) {
            hover = Math.max(hover, row.hover.at(now));
        }
        return String.format(java.util.Locale.ROOT,
                "页=%s 样例=%s 预览卡区=(x%.0f y%.0f w%.0f h%.0f) 强调条=%.2f 预览=%s 最大行悬停=%.2f 画了=%s",
                page.label(), sample.label(), card.x(), card.y(), card.w(), card.h(),
                tabAccentAnim.at(now),
                preview.stateDump(),
                hover, paintedDump());
    }

    /**
     * 这一帧画过几个控件 —— <b>"控件在、也能点、就是没画"的自检</b>。
     * <p>【为什么要有这一行】标签列在重构里整列没画过一次：点击照旧有效、单测照旧全绿，
     * 只有截图能看出来。
     */
    private String paintedDump() {
        return paintedCount() + "/" + widgetCount();
    }

    /** 这一帧画过几个控件。 */
    public int paintedCount() {
        int painted = 0;
        for (NvgWidget w : widgets()) {
            if (w.paintedIn(now)) {
                painted++;
            }
        }
        return painted;
    }

    /** 这一帧一共有几个控件。 */
    public int widgetCount() {
        return widgets().size();
    }

    /** 给 harness 用：<b>假装鼠标停在某一行上</b>（null = 取消）。 */
    public void hoverForHarness(String label) {
        forcedHover = label;
    }

    /** 宿主的 GUI 倍数（逻辑 -> 设备）= Minecraft 的 GUI Scale。 */
    private float guiScale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    /**
     * 一个设备像素（逻辑单位）。网格间距由宿主给 —— 框架不知道设备有多大。
     *
     * <p>【与 {@link #guiScale()} 必须同源】布局要网格（{@code 1/guiScale}）、接画布要对齐倍数
     * （{@code guiScale}）—— 两个数分开写的话，"对齐"会错在别人看不见的地方。
     */
    private float deviceGrid() {
        return 1f / guiScale();
    }

    /**
     * 这一帧配置列的滚动偏移。
     *
     * <p>【为什么取整再给出去】宿主自己那份（{@code ConfigRows.layout}）就是
     * {@code - Math.round(scrollOffset)}；给树的必须是同一个数，否则两边差半个像素，
     * 而"半个像素的差"在命中上会变成"点到了隔壁那一行"。
     */
    private float scrollOffset() {
        return itemsScroll == null ? 0f : Math.round(itemsScroll.offset());
    }

    /** 探针这一帧用的指针位置：harness 指定过就用它，否则用真指针。 */
    private float probePointerX() {
        return Float.isNaN(probePointerX) ? frameMouseX : probePointerX;
    }

    /** 同上。 */
    private float probePointerY() {
        return Float.isNaN(probePointerY) ? frameMouseY : probePointerY;
    }

    /**
     * 给 harness 用：让探针把指针当作落在某一行的控件中心上。
     *
     * <p>【为什么不是挪真指针】试过 {@code GLFW.glfwSetCursorPos}：GLFW 的 cursor 回调
     * <b>只在窗口有输入焦点时才发</b>，自动化跑的那扇窗通常没焦点 —— 挪了等于没挪，
     * 截图里那条悬停带根本不出现（2026-09-21 实测）。所以这里给探针一个指定的点。
     *
     * <p>【为什么这样做不影响要验的东西】几何、命中、绘制三件都还是 Trellis 的，
     * 只是"指针在哪"由 harness 说了算 —— 宿主自己的悬停测试（{@code forcedHover}）也是这样。
     *
     * <p>【几何也问树（A-10 第二步）】控件不再存 {@code x/y/w/h}，"这一行控件中心"由
     * {@code TrellisBridge.controlBox} 给 —— 与命中、绘制、拖拽读的是同一个矩形。
     */
    public boolean pointProbeAtForHarness(String label) {
        List<ConfigRows.Row> rows = rowsModel.all();
        UiTree probe = trellisProbe();
        for (int i = 0; i < rows.size(); i++) {
            ConfigRows.Row row = rows.get(i);
            if (row.isHeader() || !row.label().equals(label)) {
                continue;
            }
            Rect box = TrellisBridge.controlBox(probe, i);
            if (box == null) {
                return false;
            }
            probePointerX = box.x() + box.width() / 2f;
            probePointerY = box.y() + box.height() / 2f;
            return true;
        }
        return false;
    }

    /** 三列几何（每次现算，纯函数）。 */
    private ConfigLayout layout() {
        return ConfigLayout.compute(this.width, this.height);
    }

    /**
     * Trellis 探针：一棵<b>组件</b>树（不是裸布局节点），所以命中和绘制读的是同一个
     * {@code bounds()} 对象 —— 判据 1 靠它才能在真机上验。
     *
     * <p>【为什么不每帧重建】{@link UiTree} 带着悬停/按压/焦点状态，重建就把状态丢了；
     * 只有行数变了（换页）才重建。
     */
    private UiTree trellisProbe;
    private int trellisProbeRows = -1;
    private int trellisProbeControls = -1;
    /** harness 指定的探针指针（NaN = 用真指针）。见 {@link #pointProbeAtForHarness}。 */
    private float probePointerX = Float.NaN;
    private float probePointerY = Float.NaN;
    /** 这一帧 MC 交给 render 的指针位置（逻辑坐标）。探针默认读它。 */
    private float frameMouseX;
    private float frameMouseY;
    /** Trellis 的文本度量器，主字体是 MC 自己的字体（见 {@code McFont}）。 */
    private TextMeasurer trellisText;

    /** 探针那棵树，按当前行的<b>形态</b>（几行、几个控件行）惰性建/重建。 */
    private UiTree trellisProbe() {
        List<ConfigRows.Row> rows = rowsModel.all();
        NvgWidget[] controls = new NvgWidget[rows.size()];
        int present = 0;
        for (int i = 0; i < rows.size(); i++) {
            controls[i] = rows.get(i).isHeader() ? null : rows.get(i).widget();
            if (controls[i] != null) {
                present++;
            }
        }
        if (trellisProbe == null || trellisProbeRows != rows.size()
                || trellisProbeControls != present) {
            trellisProbe = TrellisBridge.buildColumn(controls, ConfigRows.ROWS_TOP_INSET);
            // 【建完必须当场布局】事件（点击/拖拽）落在两次 render 之间，而 {@code bounds()} 要
            // 布局过才有值 —— 只建不摆的话，"树刚作废、下一帧还没到"时来的那次点击会撞
            // NullPointerException（真机第 21 轮就是这么崩的：`Component.node()` is null）。
            // 布局是纯函数、每帧还会再算一次，多算这一遍没有副作用。
            TrellisBridge.layoutColumn(trellisProbe, layout().items(), scrollOffset(), deviceGrid());
            trellisProbeRows = rows.size();
            trellisProbeControls = present;
            // 树一重建，每一行的标签盒子就换了一批 —— 那一帧把适配结果打进日志
            labelFitLogPending = true;
        }
        return trellisProbe;
    }

    /**
     * 这一帧把树推到最新：时刻 → 布局（含滚动偏移）→ 指针 → 把悬停推给控件。
     *
     * <p>【为什么从 drawChrome 里搬出来、单独一趟】悬停缓动（{@link #driveAnimations}）、
     * 底部说明、点击路由都要问"指着哪一行"，而它们有的发生在绘制之前 ——
     * 树必须先算完。绘制那一趟因此只剩 {@link TrellisBridge#paint}。
     */
    private void updateTrellisProbe() {
        UiTree probe = trellisProbe();
        probe.tick(now * 1_000_000L);
        TrellisBridge.layoutColumn(probe, layout().items(), scrollOffset(), deviceGrid());
        probe.pointerMove(probePointerX(), probePointerY());
        TrellisBridge.syncHover(probe);     // 悬停只有一份真相：树判，控件收
    }

    /**
     * 标签适配的日志闸门：<b>树重建的那一帧打一次</b>（换页/换语言/改窗口都会重建）。
     *
     * <p>【为什么这件事必须进日志】"长文案不再整体缩小"是这一轮的验收里唯一一条
     * 只有数字能定的：盒宽、量出来的宽、缩放倍数、有没有到地板。截图看得出"变清楚了"，
     * 看不出"缩了多少、有没有溢出"。
     */
    private boolean labelFitLogPending;

    /**
     * 给 harness 用：A-10 的悬停路由读数。
     *
     * <p>【为什么必须能读出来】"带子画在哪一行、缓动跟着哪一行、说明说的是哪一行"
     * 是三个不同的消费者，但它们必须指同一行。截图只看得到第一条；后两条要靠这几个数。
     */
    public String probeDump() {
        UiTree probe = trellisProbe();
        return String.format(java.util.Locale.ROOT,
                "探针指针=(%.1f,%.1f) 树判控件行=%d 树悬停=%s",
                probePointerX(), probePointerY(),
                TrellisBridge.controlRowAt(probe, probePointerX(), probePointerY()),
                probe.hovered() == null ? "无" : probe.hovered().bounds().toString());
    }

    /** 配置项那一列的滚动视口；每帧按当前几何重建（画布会变，视口跟着变）。 */
    private NvgScroll itemsScroll;
    /** 换页清零、同页重建（删一条规则）保留 —— 列表忽然跳回顶上是纯惊吓。 */
    private float savedScrollOffset;

    /** 这一帧配置项内容有多高（滚动的依据）。 */
    private float itemsContentHeight() {
        return rowsModel.contentHeight();
    }

    /** 当前页。 */
    private ConfigPageSpec.Page page = ConfigPageSpec.Page.GENERAL;
    /** 当前预览样例。 */
    private PreviewStage.Sample sample = PreviewStage.Sample.COMMON;
    /** 本页的配置行（重建时从 {@link ConfigPageSpec} 现取；恢复默认按它跑）。 */
    private List<ConfigPageSpec.Row> specRows = List.of();
    /** 一行 = 一个标签 + 一个自绘控件 + 一句悬停提示 + 这一行的悬停进度。 */
    private final ConfigRows rowsModel = new ConfigRows();
    private final List<NvgWidget> tabButtons = new ArrayList<>();
    /** 预览底下那排「切样例」按钮（预览收起时它们是零矩形，点不到）。 */
    private final List<NvgWidget> sampleButtons = new ArrayList<>();
    /**
     * <b>树外控件的几何</b>（标签列、切样例按钮）：它们不在树的几何里，由排布者自己拿着 ——
     * 每个控件仍然只有<b>一个</b>出处（控件自己那份 {@code x/y/w/h} 已随 A-10 第二步删掉）。
     */
    private final Map<NvgWidget, Rect> chipBoxes = new IdentityHashMap<>();
    private NvgPalette palette = NvgPalette.dark(StyleModel.Accents.defaults());
    /** 控件里点出来的"切换分类/重建"请求：不在事件遍历中途重建列表。 */
    private boolean pendingRebuild;
    /** 同页重建（规则增删）保留滚动偏移；换页清零。 */
    private boolean preserveScroll;
    /** 加规则被拒时说的一句话，短时间内在底部那行顶掉悬停说明。 */
    private String filterNote = "";
    private long filterNoteAt;

    /** 预览舞台：动画页的自动舞台 + 其他页的静止卡（身份、节拍、落位全在它那里）。 */
    private final PreviewStage preview = new PreviewStage();

    /** 这一帧的时刻（毫秒）。动画与悬停都按它取值，一帧里只取一次。 */
    private long now;

    /** 给 harness 定住的悬停行（null = 用真实鼠标位置）。生产路径永远是 null。 */
    private String forcedHover;

    /** 标签强调条：值 = 选中那一颗的序号（小数 = 正在滑）。 */
    private final Tween tabAccentAnim = Tween.at(0f, 0L);

    /** 「位置」行的回调：显示当前锚点值、打开编辑场（界面自己才知道这两件事）。 */
    private final ConfigPageSpec.AnchorBridge anchorBridge = new ConfigPageSpec.AnchorBridge() {
        @Override
        public String anchorValueText() {
            return PickupCardConfigScreen.this.anchorValueText();
        }

        @Override
        public void openEditor() {
            PickupCardConfigScreen.this.openAnchorEditor();
        }
    };

    /** 过滤页的回宿：摆行、把拒绝原因说到底部那行、请求同页重建。 */
    private final FilterPageBuilder.Host filterHost = new FilterPageBuilder.Host() {
        @Override
        public void cell(String label, NvgWidget widget, String hint) {
            PickupCardConfigScreen.this.cell(label, widget, hint);
        }

        @Override
        public void rejectNote(String message) {
            filterNote = message;
            filterNoteAt = System.currentTimeMillis();
        }

        @Override
        public void requestRebuild(boolean preserve) {
            preserveScroll = preserve;
            pendingRebuild = true;
        }
    };

    /**
     * 分段按钮（标签列与样例切换共用）：<b>选中态自己会亮</b>。
     * <p>【为什么不再用 "▸ " 前缀】四个标签都挂前缀时，字宽被吃掉一大截，而且状态是
     * "文字里的一个符号"这件事本身就不该由文字承担 —— 底色和强调条说这件事更快。
     */
    private static final class Chip extends NvgWidget {

        private final Supplier<String> text;
        private final BooleanSupplier selected;
        private final Runnable action;
        private final boolean leftAligned;
        private String hint;

        Chip(String label, Supplier<String> text, BooleanSupplier selected, Runnable action,
             boolean leftAligned) {
            super(label);
            this.text = text;
            this.selected = selected;
            this.action = action;
            this.leftAligned = leftAligned;
        }

        Chip hint(String hint) {
            this.hint = hint;
            return this;
        }

        @Override
        public String value() {
            return text.get();
        }

        @Override
        protected void onActivate() {
            action.run();
        }

        /** 几何从 {@code ctx.box()} 来：树外控件的盒子由排布者交进来（{@code chipBoxes}）。 */
        @Override
        protected void paint(PaintCtx ctx) {
            NvgPalette p = ctx.palette();
            float w = ctx.width();
            float h = ctx.height();
            boolean on = selected.getAsBoolean();
            ctx.well(0f, 0f, w, h, on ? p.wellHover : wellColor(p));
            if (on) {
                // 选中那颗描一圈强调色：底色一档差别在深色主题下太细，看不清"我在哪一页"
                ctx.strokeRoundRect(0f, 0f, w, h, p.radius, NvgUi.fade(p.accent, 0.5f));
            }
            float ty = (h - ctx.lineHeight()) / 2f;
            int color = on ? p.text : p.textDim;
            if (leftAligned) {
                // 【缩字不穿列】页签标签左对齐，英文页名（"Placement & stacking"）比中文
                // 宽一截，直画会穿出胶囊叠到配置列小节头上（2026-09-19 英文截图抓到）
                ctx.textFitted(text.get(), 4f, ty, color, w - 8f);
            } else {
                // 【缩字不换行】整行装不下被挤压时，芯片里的文字跟着缩（英文样例名
                // "Long name" 在窄窗口必然超宽），不叠到邻居头上
                ctx.textCenteredFitted(text.get(), w / 2f, ty, color, w - 6f);
            }
        }
    }

    public PickupCardConfigScreen(Screen parent) {
        super(Component.translatable("pickupcard.config.title"));
        this.parent = parent;
        // 预览一打开就核对样例的真实稀有度（只跑一次）—— 标称与实际不符会在日志里报 ERROR
        PreviewStage.Sample.verify();
    }

    private final Screen parent;

    // ------------------------------------------------------------------
    // 搭界面
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        rebuild();
        now = System.currentTimeMillis();
        tabAccentAnim.snap(page.ordinal());
    }

    private void rebuild() {
        boolean keep = preserveScroll;
        preserveScroll = false;
        rowsModel.all().clear();
        tabButtons.clear();
        sampleButtons.clear();
        // 树外控件的几何表：重建时一并换掉（每一颗芯片的格子都是这一帧重算的）
        chipBoxes.clear();
        // 【树也要作废】重建换了一批 widget 实例，而树里的 ControlSlot 认的是实例身份。
        // 只判"行数/控件数变了没有"的话，**同形重建**（行没变、值变了：恢复默认、删一条规则）
        // 会留下一棵拿着旧实例的树 —— 于是绘制与按下走旧实例，而键盘、读数、boxOf 走新实例。
        // 从前这条只影响命中，A-10 第二步起树自己画控件，影响面扩大到"屏幕画的是哪批实例"。
        trellisProbe = null;
        trellisProbeRows = -1;
        trellisProbeControls = -1;
        palette = NvgPalette.of(CardStage.INSTANCE.previewStyle());
        StyleModel style = CardStage.INSTANCE.previewStyle();
        PickupCardSettings eff = PickupCardConfig.snapshot();
        // 页面归属、控件、默认值 —— 全部只有注册表这一个出处
        specRows = ConfigPageSpec.rows(style, eff, anchorBridge);

        ConfigLayout lo = layout();
        ConfigPageSpec.Page[] pages = ConfigPageSpec.Page.values();
        for (int i = 0; i < pages.length; i++) {
            ConfigPageSpec.Page p = pages[i];
            Chip chip = new Chip(p.label(), () -> p.label(), () -> p == page, () -> {
                if (p == page) {
                    return;     // 点当前这页：什么都不做
                }
                page = p;
                pendingRebuild = true;      // 换页：瞬间换内容（无动画），滚动清零
            }, !lo.tabsOnTop()).hint(p.hint());
            ConfigLayout.Rect cellRect = lo.tabRect(i, pages.length);
            chipBoxes.put(chip, new Rect(cellRect.x(), cellRect.y(), cellRect.w(), cellRect.h()));
            tabButtons.add(chip);
        }

        PreviewStage.Sample[] samples = PreviewStage.Sample.values();
        int switchCount = samples.length + 1;      // 样例 + 「来一张」
        // 【等分改内容宽】等分是按中文两字标签定的；语言一换（Common / Experience /
        // Spawn one）文本就溢出芯片叠到邻居头上（英文截图抓到的）。每颗按自己的文案
        // 量宽，整行装不下时整行按比例压 —— 窄窗口牺牲余白，不牺牲可读性。
        ConfigLayout.Rect row0 = lo.switchRect(0, switchCount);
        ConfigLayout.Rect rowN = lo.switchRect(switchCount - 1, switchCount);
        float chipGap = 3f;
        float[] chipWidths = new float[switchCount];
        float widthsTotal = 0f;
        for (int i = 0; i < switchCount; i++) {
            String label = i < samples.length ? samples[i].label()
                    : I18n.get("pickupcard.config.button.spawn");
            chipWidths[i] = Math.max(row0.h(), this.font.width(label) + 10f);
            widthsTotal += chipWidths[i];
        }
        float squeeze = Math.min(1f, (rowN.x() + rowN.w() - row0.x() - chipGap * (switchCount - 1))
                / widthsTotal);
        float chipX = row0.x();
        for (int i = 0; i < switchCount; i++) {
            float w = chipWidths[i] * squeeze;
            Chip chip;
            if (i < samples.length) {
                PreviewStage.Sample s = samples[i];
                chip = new Chip(s.label(), () -> s.label(), () -> s == sample, () -> {
                    if (s == sample) {
                        return;
                    }
                    sample = s;
                    // 选中的样例立刻上台/上纸：改了"下一张是谁"当场看得见
                    preview.playOnce(now, s);
                }, false).hint(s.hint());
            } else {
                // 「来一张」：手动入口 —— 想仔细看入场/退场，点它当场放一张，不用等节拍。
                chip = new Chip(I18n.get("pickupcard.config.button.spawn"),
                        () -> I18n.get("pickupcard.config.button.spawn"), () -> false,
                        () -> preview.playOnce(now, sample), false)
                        .hint(I18n.get("pickupcard.config.button.spawn.hint"));
            }
            // 位置每次重建算的：预览会不会出现、切换行画不画，都取决于画布大小
            chipBoxes.put(chip, new Rect(chipX, row0.y(), w, row0.h()));
            chipX += w + chipGap;
            sampleButtons.add(chip);
        }

        itemsScroll = new NvgScroll(lo.items().x(), lo.items().y(), lo.items().w(), lo.items().h());
        itemsScroll.scrollTo(keep ? savedScrollOffset : 0f);

        // 预览按页分工（2026-09-19 grill 定案）：动画页舞台、位置页整屏缩影、其余页静止特写
        preview.setMode(previewMode(), sample);

        restoreRow(page);
        if (page == ConfigPageSpec.Page.FILTER) {
            FilterPageBuilder.build(PickupCardConfig.VALUES, filterHost);
        } else {
            for (ConfigPageSpec.Row row : specRows) {
                if (row.page() != page) {
                    continue;
                }
                if (row.isHeader()) {
                    header(row.label());
                } else {
                    cell(row.label(), row.widget().get(), row.hint());
                }
            }
        }
        layoutRows();
    }

    /** 预览的分工：动画页跑三张真卡的自动舞台；位置页整屏缩影；其余页一张静止完整卡。 */
    private boolean stagePage() {
        return page == ConfigPageSpec.Page.ANIM;
    }

    private PreviewStage.Mode previewMode() {
        if (page == ConfigPageSpec.Page.ANIM) {
            return PreviewStage.Mode.STAGE;
        }
        if (page == ConfigPageSpec.Page.LAYOUT) {
            return PreviewStage.Mode.MINIMAP;
        }
        return PreviewStage.Mode.STATIC;
    }

    /** 「恢复本页默认」那一行：每页第一行，行动作、不是配置项。项数现算，不再手抄。 */
    private void restoreRow(ConfigPageSpec.Page restored) {
        int n;
        if (restored == ConfigPageSpec.Page.FILTER) {
            n = FilterPageBuilder.RESTORE_COUNT;
        } else {
            n = 0;
            for (ConfigPageSpec.Row row : specRows) {
                if (row.page() == restored && row.restorable()) {
                    n++;
                }
            }
        }
        cell(I18n.get("pickupcard.config.button.restore"),
                new NvgButton("", () -> I18n.get("pickupcard.config.button.restore.short"),
                        this::restorePageDefaults),
                ConfigPageSpec.restoreHint(restored, n));
    }

    /**
     * 「恢复本页默认」：把<b>这一页</b>看得见的项写回出厂值。
     * <p>【默认值从哪来】不再手抄 —— 注册表里每一行的恢复动作是
     * {@code value.set(value.getDefault())}，出厂值只有 TOML 键定义一个出处。
     * 【位置页为什么不重置锚点】锚点是玩家在编辑场里亲手拖的刻意选择，混进"一键恢复"
     * 里一个手滑就没了 —— 它的「回到默认」在编辑场自己那颗钮上。
     */
    private void restorePageDefaults() {
        if (page == ConfigPageSpec.Page.FILTER) {
            FilterPageBuilder.restoreDefaults(PickupCardConfig.VALUES);
        } else {
            for (ConfigPageSpec.Row row : specRows) {
                if (row.page() == page && row.restorable()) {
                    row.restore().run();
                }
            }
        }
        ConfigPageSpec.changed();
        pendingRebuild = true;      // 行没变但值全变：重建让控件显示活配置
    }

    private void cell(String label, NvgWidget widget, String hint) {
        rowsModel.cell(label, widget, hint);
    }

    /** 小节头：占一行、不接控件，把"这几行是一伙的"画出来。 */
    private void header(String title) {
        rowsModel.header(title);
    }

    /** 逐行摆：**一行一项**（标签左、控件右），行距全界面恒 20px，放不下就滚。 */
    private void layoutRows() {
        if (itemsScroll == null) {
            return;
        }
        itemsScroll.reflow(itemsContentHeight());
        // 逐行摆：行模型（ConfigRows）只认几何，屏幕把滚动偏移与列顶交给它
        rowsModel.layout(layout(), itemsScroll.offset(), rowsTop());
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        long frameStart = System.nanoTime();
        now = System.currentTimeMillis();
        frameMouseX = mouseX;
        frameMouseY = mouseY;
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
        layoutRows();       // 每帧刷一遍：滚一下、换一页、改窗口尺寸，位置都要跟上
        // 【顺序不能换】树要先算完（含滚动偏移与指针），下面三件事才问得到"指着哪一行"：
        // 悬停缓动、树外的控件悬停、以及绘制那趟里树的 paint。
        updateTrellisProbe();
        driveAnimations();
        for (NvgWidget w : chips()) {
            // 树外的控件（标签列、切样例按钮）：命中还是自己判 —— 它们不在树的几何里
            Rect box = chipBoxes.get(w);
            w.hover(box != null && box.contains(mouseX, mouseY));
        }

        gui.fill(0, 0, this.width, this.height, palette.backdrop);
        boolean chromePainted = false;
        try (NvgUi ui = NvgUi.begin(gui, palette, mouseX, mouseY, now)) {
            if (ui != null) {
                chromePainted = true;
                // 【一帧一个"表面"】画布（接进宿主上下文，带 GUI 倍数）、配色、字形缝、时刻 ——
                // 树内控件与树外控件都拿它画自己（A-10 第二步）。倍数必须传：接进来的画布没有
                // begin，不传它按 1 算，设备像素对齐会退化成"对齐到整数逻辑坐标"。
                TrellisBridge.Frame surface = TrellisBridge.surface(ui.canvas(), palette,
                        new McGlyphPainter(ui), now, guiScale());
                drawChrome(ui);
                drawTabAccent(ui);
                // 【标签列必须自己画一遍】它不参与配置列的裁剪与换页淡入（换页时它不动）；
                // 几何由排布者给（{@code chipBoxes}）。
                for (NvgWidget w : tabButtons) {
                    w.draw(surface.ctxFor(chipBoxes.get(w)));
                }
                // 配置项那一列：裁剪到视口里 —— 滚出去的行不许糊在标签列或预览列上。
                if (itemsScroll != null) {
                    itemsScroll.pushClip(ui);
                }
                drawLabelMarks(ui);
                // 【控件本体那一趟（A-10 第二步）】悬停底与控件本体都由组件树画：绘制、命中、
                // 拖拽读的是同一个 bounds()；控件的位置也从它来（A-4 那 1.3px 由此归零）。
                // 【层序：控件本体与旧路径同位置；悬停底是挪过的】改由树画的悬停底从前在
                // drawChrome（配置项的裁剪之外），现在跟树一起进了裁剪 —— 顺带修掉"滚出视口的行，
                // 悬停带还糊在标签列上"。详见 {@code TrellisBridge.paint} 的说明。
                TrellisBridge.paint(surface, trellisProbe());
                if (itemsScroll != null) {
                    drawScrollBar(ui);
                    ui.popClip();
                }
                for (NvgWidget w : sampleButtons) {
                    w.draw(surface.ctxFor(chipBoxes.get(w)));
                }
            }
        }
        // 标签列的文字：位置与适配是 Trellis 的，字形是 MC 的（A-7 那条分工）。
        // 【为什么在 NvgUi 那一拍之后】原版批次本身就是延后提交的（`NvgUi.text` 也是登记到
        // close() 才画），所以这一趟的层次与从前逐字一致；而它需要一个已经算好的树
        // （drawChrome 里那一趟布局），以及"不能被 NanoVG 的即时路径盖住"的次序。
        // 【为什么门控在 chromePainted 上】NanoVG 起不来时这一帧本来就"没有界面"
        // （begin 的契约）：只把字画上去，等于在没有底的地方浮一层文字；而且树那一帧
        // 根本没布局过，读 box 会读到空节点。
        if (chromePainted) {
            drawLabels(gui);
        }
        // 预览放在最后：它自己开一帧（里面还有原版图标与文字），压在自绘界面之上。
        long previewStart = System.nanoTime();
        drawPreview(gui);
        previewNanos = System.nanoTime() - previewStart;
        drawHint(gui, mouseX, mouseY);
        drawTrellisTextSample(gui);
        frameCost(System.nanoTime() - frameStart);
    }

    /** 试点的样本文本：故意带中文、带中点，专门试 MC 字形 + Trellis 摆位。 */
    private static final String TRELLIS_SAMPLE = "拾取卡片·中文";

    /** 惰性建（第一次画时才需要 MC 的字体）。 */
    private TextMeasurer trellisText() {
        if (trellisText == null) {
            trellisText = new TextMeasurer(FontStack.of(new McFont()));
        }
        return trellisText;
    }

    /**
     * A-7 验收：<b>位置由 Trellis 算，字形由 MC 画</b>。
     *
     * <p>画在一块空白上（第一行小节头的右边 —— 小节头没有控件，右边全空），
     * 画两样东西：Trellis 量出来的<b>文本框</b>，和 MC 用<b>它自己的字形</b>画的那行字。
     * 字画完的墨迹应当正好落在框里 —— 这一步成立，"文本框和背景谁是基准"才算有答案。
     *
     * <p>MC 画字吃的是"行顶"，而 Trellis 给的是基线，所以这里减 MC 自己的 ascent
     * （{@link McFont#ASCENT_PX}）—— 两边都是声明过的数，没有临时拼的。
     */
    private void drawTrellisTextSample(GuiGraphics gui) {
        List<ConfigRows.Row> rows = rowsModel.all();
        ConfigRows.Row header = null;
        for (ConfigRows.Row row : rows) {
            if (row.isHeader()) {
                header = row;
                break;
            }
        }
        if (header == null) {
            return;     // 这一页没有小节头，样本没地方放
        }
        TextLayout layout = trellisText().measure(TRELLIS_SAMPLE, McFont.EM);
        float x = ConfigRows.labelX(layout()) + 34f;    // 让开小节头自己的标题
        float baselineY = header.yAt + 4f + layout.ascent();

        gui.fill((int) x - 1, (int) (baselineY - layout.ascent()) - 1,
                (int) (x + layout.width()) + 1, (int) (baselineY + layout.descent()) + 1,
                0x40FFFFFF);
        net.minecraft.client.gui.Font mc = Minecraft.getInstance().font;
        gui.drawString(mc, TRELLIS_SAMPLE, (int) x,
                Math.round(baselineY - McFont.ASCENT_PX), 0xFF101010, false);

        if (!trellisSampleLogged) {
            trellisSampleLogged = true;
            PickupCard.LOGGER.info(
                    "[trellis-text] 样本『{}』 Trellis 量到宽 {} px（{} 簇、画不出 {} 个），"
                            + "MC 自己量到宽 {} px —— 两个数相等才算度量与字形同源",
                    TRELLIS_SAMPLE, layout.width(), layout.clusterCount(), layout.undrawableCodePoints(),
                    mc.width(TRELLIS_SAMPLE));
        }
    }

    private boolean trellisSampleLogged;

    /**
     * 逐帧耗时统计 —— 用户报过「配置界面动画掉帧」，而掉帧只有数字能定死。
     * 记<b>最大值</b>与"超过 16.7ms 的帧数"，每 {@value #FRAME_LOG_EVERY} 帧报一次。
     */
    private void frameCost(long nanos) {
        long us = nanos / 1_000L;
        previewSumUs += previewNanos / 1_000L;
        frameCount++;
        if (us > frameMaxUs) {
            frameMaxUs = us;
            previewAtMaxUs = previewNanos / 1_000L;
            frameMaxAt = frameCount;
        }
        frameSumUs += us;
        if (us > 16_700L) {
            frameJanky++;
        }
        if (frameCount >= FRAME_LOG_EVERY) {
            reportFrames();
        }
    }

    /** 报一次统计并把窗口清零（关界面时也报一次，免得"来了一趟却什么都没量到"）。 */
    private void reportFrames() {
        if (frameCount == 0) {
            return;
        }
        PickupCard.LOGGER.info("[配置界面/帧] {} 帧：平均 {}us（预览均摊 {}us），最慢 {}us（其中预览 {}us，第 {} 帧），超过 16.7ms 的有 {} 帧",
                frameCount, frameSumUs / frameCount, previewSumUs / frameCount,
                frameMaxUs, previewAtMaxUs, frameMaxAt, frameJanky);
        frameCount = 0;
        frameSumUs = 0L;
        frameMaxUs = 0L;
        frameMaxAt = 0;
        frameJanky = 0;
        previewSumUs = 0L;
        previewAtMaxUs = 0L;
    }

    @Override
    public void removed() {
        reportFrames();
        super.removed();
    }

    /** 每多少帧报一次（约 10 秒 @60fps）。 */
    private static final int FRAME_LOG_EVERY = 600;
    private int frameCount;
    private long frameSumUs;
    private long frameMaxUs;
    private int frameMaxAt;
    private int frameJanky;
    /** 本窗口内预览列的耗时累计。 */
    private long previewNanos;
    private long previewSumUs;
    private long previewAtMaxUs;

    /**
     * 把这一帧的动画目标推进一步。
     * <p>【为什么统一在渲染前推】一个动画一件事：换页与强调条的目标恒为/随状态，悬停跟着鼠标。
     * 分散在各自的绘制里推进的话，"这一帧到底更新过谁"就没人说得清了。
     */
    private void driveAnimations() {
        preview.drive(now, sample);
        tabAccentAnim.retarget(page.ordinal(), now, TAB_MS);
        // 悬停哪一行<b>问树</b>：它读的是 Component.bounds()，跟悬停底、命中是同一份几何。
        // 指针位置用探针那一份（harness 可以让它假装停在某一行上）—— 这样缓动的行与
        // 画出来的带子必然是同一条，不会出现"带子在这行、字亮的是那行"。
        int hoveredControl = TrellisBridge.controlRowAt(trellisProbe(),
                probePointerX(), probePointerY());
        List<ConfigRows.Row> rows = rowsModel.all();
        for (int i = 0; i < rows.size(); i++) {
            ConfigRows.Row row = rows.get(i);
            if (row.isHeader()) {
                continue;
            }
            boolean on = forcedHover != null
                    ? row.label().equals(forcedHover)
                    : i == hoveredControl;
            row.hover.retarget(on ? 1f : 0f, now, on ? HOVER_IN_MS : HOVER_OUT_MS);
        }
    }

    /** 底 + 标题 + 标签列 + 预览面板 —— 全是 NanoVG 画的圆角块。 */
    private void drawChrome(NvgUi ui) {
        NvgPalette p = ui.palette;
        ConfigLayout lo = layout();
        // 【悬停底与控件本体不在这一趟】它们在组件树那一趟里画（{@code TrellisBridge.paint}，
        // 见 render 的"控件本体那一趟"）—— 命中、悬停、拖拽与绘制读的是同一个 bounds()（判据 1）。
        PickupCardSettings eff = PickupCardConfig.snapshot();
        // 标题靠左、副标题跟同一个左缘（用户要求标题不居中；对齐 MARGIN 与标签列同一起点）。
        // 状态行钉在标题行右端 —— 总开关是"整体生效没生效"的唯一真源，藏进页里就得翻页才知道。
        ui.text(this.title.getString(), ConfigLayout.MARGIN, 6f, 0xFFFFFFFF);
        ui.text(I18n.get("pickupcard.config.subtitle"), ConfigLayout.MARGIN, 17f, p.textDim);
        ui.textRight(status(), this.width - PAD - 4f, 6f,
                eff.enabled() ? p.accent : p.textDim);
        // 标题和内容之间那条线：没有它，标题行和第一行标签会连成一片
        ui.fillRoundRect(ConfigLayout.MARGIN, ConfigLayout.TOP - 5f,
                Math.max(0f, this.width - ConfigLayout.MARGIN * 2f), 1f, 0.5f,
                NvgUi.fade(p.outline, 0.6f));
        // 标签那一列：列排时是一竖条底，顶排时是一横条底
        ui.fillGradient(lo.tabs().x() - 2f, lo.tabs().y() - 2f, lo.tabs().w() + 4f,
                lo.tabs().h() + 4f, p.panel, 0x80202836);
        // 预览列：面板底 + 标题（收掉时这两样都不画）
        if (lo.previewVisible()) {
            ui.text(I18n.get("pickupcard.config.preview"), lo.preview().x(), lo.preview().y(), p.textDim);
            ui.fillRoundRect(lo.preview().x() - 2f, lo.preview().y() + 10f,
                    lo.preview().w() + 4f, Math.max(0f, lo.preview().h() - 12f), p.radius,
                    0x40202A38);
        } else {
            // 「预览被收掉了」的提示挪到 drawHint 那一拍用 raw gui 画：在这里画的话，
            // 垫底带属于 NanoVG 即时路径、会被控件底盖住，而字又要等 close() 才上屏
            // —— 底和字分家，字就浮在别人控件上了。
        }
    }

    /** 标题行右端那行状态：总开关之外，玩家最常想知道的是"卡现在缩到了多少"。 */
    private String status() {
        PickupCardSettings eff = PickupCardConfig.snapshot();
        return I18n.get(eff.enabled() ? "pickupcard.config.status.on" : "pickupcard.config.status.off")
                + scaleText();
    }

    /** 底部那行「预览被收掉了」的提示语。方法而不是常量：文案要在取用的那一刻解析。 */
    private static String previewCollapsed() {
        return I18n.get("pickupcard.config.preview.collapsed");
    }

    /** 底部那行<b>左边</b>（悬停说明）最多能画到哪个 x。 */
    private float hintRightLimit() {
        float limit = this.width - PAD - 4f;
        ConfigLayout lo = layout();
        if (!lo.previewVisible()) {
            limit = Math.min(limit,
                    lo.items().right() - this.font.width(previewCollapsed()) - PAD);
        }
        return limit;
    }

    /**
     * 选中那颗标签的强调条：从上一颗<b>滑</b>到这一颗。
     * <p>底色那一档差别在深色主题下太细，加一条强调色带之后，换页这件事在眼睛的余光里也成立。
     */
    private void drawTabAccent(NvgUi ui) {
        ConfigLayout lo = layout();
        int count = ConfigPageSpec.Page.values().length;
        float idx = tabAccentAnim.at(now);
        float low = Math.max(0f, Math.min(count - 1f, (float) Math.floor(idx)));
        float high = Math.max(0f, Math.min(count - 1f, (float) Math.ceil(idx)));
        ConfigLayout.Rect a = lo.tabRect((int) low, count);
        ConfigLayout.Rect b = lo.tabRect((int) high, count);
        float t = idx - low;
        float x = a.x() + (b.x() - a.x()) * t;
        float y = a.y() + (b.y() - a.y()) * t;
        if (lo.tabsOnTop()) {
            ui.fillRoundRect(x + 2f, y + a.h() - 1.5f, Math.max(0f, a.w() - 4f), 2f, 1f,
                    ui.palette.accent);
        } else {
            ui.fillRoundRect(x - 2.5f, y + 2f, 2.5f, Math.max(0f, a.h() - 4f), 1.25f,
                    ui.palette.accent);
        }
    }


    /**
     * 小节头那一根强调色小刺 —— <b>形状仍走 NanoVG</b>（文字那一半搬到 {@link #drawLabels} 了：
     * MC 的字形只能由 MC 画）。只用字号一种手段的话，小节头会和"没接控件的标签"混成一团
     * （2026-09-19 真机截图抓过），所以这一根刺留着。
     */
    private void drawLabelMarks(NvgUi ui) {
        for (ConfigRows.Row row : rowsModel.all()) {
            if (!row.isHeader()) {
                continue;
            }
            ui.fillRoundRect(labelX() - 3f, row.yAt + 5f, 2f, 8f, 1f,
                    NvgUi.fade(ui.palette.accent, 0.45f));
        }
    }

    /**
     * 标签缩字的地板（逻辑 px）：<b>到了它就不再缩，宁可溢出。</b>
     *
     * <p>这是 Trellis 文本层的纪律（字号不许小到 8 逻辑 px 以下，见 {@code TextMeasurer.shrinkToFit}）：
     * 溢出看得见（调用方能去裁、去换行、去改文案），缩到看不见没有任何东西会报。
     * 拿 MC 的 9px 正文当基准时这条地板只留了 11% 的缩字余量 —— 英文长标签会溢到控件那一侧，
     * 那是<b>设计上选的失败方式</b>，不是这一轮的意外（真机数字见 plan.md A-8）。
     */
    private static final float LABEL_MIN_FONT = 8f;

    /**
     * 截断时接在末尾的串：<b>ASCII 三个点，不是 U+2026</b>。
     * 能不能画出来是字体的事 —— MC 的默认图集里一定有 `...`，而 `…` 要靠 unifont 兜底
     * （形状与基线都跟默认字体不是一套）。这一串也参与度量，所以它画得出来才算数。
     */
    private static final String LABEL_ELLIPSIS = "...";

    /**
     * 标签列的文字：<b>文本框由 Trellis 算（树里每行的第 1 个孩子），字形由 MC 画</b>。
     *
     * <p>【为什么不再问 {@code ui.textFitted}】它的办法是"装不下就把整串字整体缩放
     * {@code k = maxW / tw}"，<b>没有下限</b> —— 长文案一路缩到看不清，而且什么都不报。
     * Trellis 的 {@code shrinkToFit} 重测一个<b>更小字号</b>（不是把画出来的东西拉伸），
     * 并且把地板写进签名；到地板还装不下就 {@code ellipsize}（少几个字，不越界）。
     * 位置也不再手算（{@code labelX() + 3 / yAt + 5} 那两份必然漂）。
     *
     * <p>【为什么文字要跟着视口裁】滚出视口的那几行，宿主自己也不画控件；标签不裁的话
     * 会糊在下面的说明带上。取整与 {@code NvgUi.close()} 提交文字时用的那一套一致。
     */
    private void drawLabels(GuiGraphics gui) {
        List<ConfigRows.Row> rows = rowsModel.all();
        UiTree probe = trellisProbe();
        ConfigLayout.Rect items = layout().items();
        StringBuilder log = labelFitLogPending ? new StringBuilder() : null;
        gui.enableScissor(Math.round(items.x()), Math.round(items.y()),
                Math.round(items.right()), Math.round(items.bottom()));
        try {
            for (int i = 0; i < rows.size(); i++) {
                ConfigRows.Row row = rows.get(i);
                Rect box = TrellisBridge.labelBox(probe, i);
                TrellisBridge.LabelFit fit = TrellisBridge.fitLabel(trellisText(), row.label(),
                        box, McFont.EM, LABEL_MIN_FONT, LABEL_ELLIPSIS);
                // 悬停时标签由暗到亮：它、那条高亮带、底部那句说明指的是同一行
                int argb = row.isHeader()
                        ? palette.textDim
                        : NvgUi.mix(palette.textDim, palette.text, row.hover.at(now));
                drawLabel(gui, fit, argb);
                if (log != null) {
                    if (log.length() > 0) {
                        log.append(" | ");
                    }
                    log.append(row.label()).append("：盒").append(Math.round(box.width()))
                            .append(" 量").append(String.format(java.util.Locale.ROOT, "%.1f",
                                    fit.width()))
                            .append(" 缩放").append(String.format(java.util.Locale.ROOT, "%.3f",
                                    fit.scale()))
                            .append(switch (fit.mode()) {
                                case ORIGINAL -> " 原";
                                case SHRUNK -> " 缩";
                                case ELLIPSIZED -> " 截";
                            });
                    if (fit.mode() == TrellisBridge.LabelFit.Mode.ELLIPSIZED) {
                        // 画出来的串跟着进日志：只看"截了"三个字，看不出截成了什么
                        log.append('→').append(fit.text());
                    }
                    if (fit.overflow()) {
                        // 到这一步只有一种可能：盒子窄到连省略号都放不下（见 LabelFit#overflow）
                        log.append(" 溢");
                    }
                }
            }
        } finally {
            gui.disableScissor();
        }
        if (log != null) {
            labelFitLogPending = false;
            PickupCard.LOGGER.info("[trellis-label] 标签适配（盒宽 / Trellis 量宽 / 缩放 / 原缩截 / 溢没溢）: {}",
                    log);
        }
    }

    /**
     * 画一行字：MC 的字形，Trellis 给的位置、字号与串（截断那一档串是带省略号的）。
     *
     * <p>【为什么要 pose 缩放】MC 的字体只有一个尺寸（行高 9），"字号"只能靠矩阵表达；
     * 这里的倍数来自 {@code shrinkToFit} 量出的字号（带地板），不是宽度比当场算的。
     * 缩过的那一档本来就放弃了锐度，没缩的那一档必须落在设备整数上 ——
     * 位图字形停在设备半像素上会糊，而 {@code TextAlign} 居中出来的 y 带着 .5。
     */
    private void drawLabel(GuiGraphics gui, TrellisBridge.LabelFit fit, int argb) {
        float y = fit.scale() == 1f ? Snapping.edge(fit.top(), deviceGrid()) : fit.top();
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(fit.x(), y, 0f);
        if (fit.scale() != 1f) {
            pose.scale(fit.scale(), fit.scale(), 1f);
        }
        // 阴影开着：与从前那条路（NvgUi.textFitted）一致，不给这一轮多叠一个变量
        gui.drawString(this.font, fit.text(), 0, 0, argb, true);
        pose.popPose();
    }

    /** 底部那行说明：悬停谁就说谁，这是这个界面唯一能自我解释的地方。 */
    private void drawHint(GuiGraphics gui, int mouseX, int mouseY) {
        String hint = null;
        // 加规则被拒时先说话：它是玩家刚刚做的动作的结果，比"鼠标现在停在哪"更该被看见
        if (!filterNote.isEmpty() && now - filterNoteAt < FILTER_NOTE_MS) {
            hint = filterNote;
        }
        // 夹在配置列里：滚出视口的行，它的矩形还在（只是被裁掉了）——
        // 不做这个判断的话，鼠标划过页眉时会说"这张卡的说明"，而那一行根本看不见。
        // 【哪一行也问树】跟悬停底、点击是同一份几何（A-10）；host 那条 widget().hit() 删了。
        // 【用探针那一份指针】生产环境它就是真指针；harness 可以让它假装停在某一行上。
        // 说明、带子、标签缓动必须跟着同一个指针，否则截图里会出现"带子在这行、
        // 说明说的是那行"——那种不一致只有数会露出来。
        ConfigLayout.Rect items = layout().items();
        float pointerX = probePointerX();
        float pointerY = probePointerY();
        if (hint == null && pointerY >= items.y() && pointerY < items.bottom()) {
            int row = TrellisBridge.controlRowAt(trellisProbe(), pointerX, pointerY);
            if (row >= 0) {
                hint = rowsModel.all().get(row).hint();
            }
        }
        if (hint == null) {
            for (NvgWidget chip : chips()) {
                Rect box = chipBoxes.get(chip);
                if (chip instanceof Chip c && c.hint != null && box != null
                        && box.contains((float) mouseX, (float) mouseY)) {
                    hint = c.hint;
                    break;
                }
            }
        }
        if (hint == null) {
            hint = page.hint();        // 哪儿都没停：说当前这一页是干嘛的
        }
        // 【左右边距要对称】从前预算是 hintRightLimit - PAD，左边距却只给了 PAD=6：
        // 英文一长，尾巴一直顶到离屏幕右缘 10px 的地方被 plainSubstrByWidth 硬切，
        // 切点还悬在屏幕边上（用户报的「英文文案超出 UI 边缘」就是这条）。左边距改用
        // 与标题同一把尺（MARGIN），装不下就补省略号——宁可诚实地"…"，不要假装写得下。
        float budget = Math.max(24f, hintRightLimit() - ConfigLayout.MARGIN);
        String shown = this.font.plainSubstrByWidth(hint, (int) budget);
        if (this.font.width(shown) < this.font.width(hint)) {
            shown = this.font.plainSubstrByWidth(hint,
                    (int) Math.max(0f, budget - this.font.width(ELLIPSIS))) + ELLIPSIS;
        }
        // 【让开原版 HUD】height-12 正好坐在快捷栏格子里。原版 HUD 底部中带（快捷栏 22px
        // + 经验条 + 等级数）都在底部 40px 内，height-40 在它上面、面板之下。
        // 【垫一层底】这个位置仍会浮在滚动内容与面板的尾巴上（外观页实测与"Fill top"行相撞）。
        // 【要的是遮蔽不是描边】深色界面里再叠"半透明深色"毫无对比（56% 实测看不出来）；
        // 这条底带的职责是把背后的行压掉、给自己当画布——94% 不透明才做得到。
        int bandW = this.font.width(shown);
        // 【先清批再画带】原版批按渲染类型排序、不按提交顺序：不清批的话，带（position_color）
        // 会在整个帧的文字批次【之前】上屏，被前面排队的所有标签字压回来（外观页实测
        // "Fill top" 就是这样从带里透出来的）。清批后带与提示字是最后入队的内容，顺序才对。
        gui.flush();
        gui.fill(Math.round(ConfigLayout.MARGIN) - 5, this.height - 43,
                Math.round(ConfigLayout.MARGIN) + bandW + 5, this.height - 28, 0xF010141C);
        gui.drawString(this.font, shown, Math.round(ConfigLayout.MARGIN), this.height - 40,
                palette.textDim);
        // 右端那句「预览被收掉了」：只在预览隐藏时有，与左边的说明同一拍、同一套垫底。
        // 从前它在 drawChrome（NanoVG 拍）里画，垫底会被控件底盖住、字却排队到 close()，
        // 底字分家——两样都挪到这里用 raw gui 画，先后顺序就再也错不了。
        ConfigLayout lo = layout();
        if (!lo.previewVisible()) {
            String collapsed = previewCollapsed();
            int cw = this.font.width(collapsed);
            int right = Math.round(lo.items().right());
            gui.fill(right - cw - 4, this.height - 42, right + 4, this.height - 29, 0x9010141C);
            gui.drawString(this.font, collapsed, right - cw, this.height - 40, palette.textDim);
        }
    }

    /** 需要滚动时才画的那条滚动条（细，不抢视线；位置一眼看出"还能往下"）。 */
    private void drawScrollBar(NvgUi ui) {
        float content = itemsContentHeight();
        if (!itemsScroll.scrollable(content)) {
            return;
        }
        ConfigLayout lo = layout();
        float trackH = lo.items().h() - 8f;
        float barH = Math.max(12f, trackH * (lo.items().h() / content));
        float t = ScrollMath.maxOffset(content, lo.items().h()) <= 0f ? 0f
                : itemsScroll.offset() / ScrollMath.maxOffset(content, lo.items().h());
        float x = lo.items().right() - 3f;
        float y = lo.items().y() + 4f + t * (trackH - barH);
        ui.fillRoundRect(x, y, 3f, barH, 1.5f, ui.palette.textDim);
    }

    // ------------------------------------------------------------------
    // 预览：全在 PreviewStage —— 这里只管"面板里那块地方"
    // ------------------------------------------------------------------

    /** 预览收起时什么都不画 —— 挤成一条比没有更难看。 */
    private void drawPreview(GuiGraphics gui) {
        ConfigLayout lo = layout();
        if (!lo.previewVisible()) {
            return;
        }
        ConfigLayout.Rect area = lo.previewCard();
        if (area.w() <= 2f || area.h() <= 2f) {
            return;
        }
        preview.render(gui, area, this.width, this.height,
                CardStage.INSTANCE.previewStyle(), now);
    }

    /** 标题右上角那行：总开关之外，玩家最常想知道的是"卡现在缩到了多少"。 */
    private static String scaleText() {
        int pct = PickupCardConfig.layoutSnapshot().scalePercent();
        return I18n.get("pickupcard.config.scale",
                pct > LayoutSettings.AUTO_SCALE ? pct + "%" : I18n.get("pickupcard.config.value.auto"));
    }

    // ------------------------------------------------------------------
    // 事件：全部转给自绘控件
    // ------------------------------------------------------------------

    /** 所有自绘控件：事件遍历、悬停刷新、重建时都用这一份（小节头没有控件，不在其中）。 */
    private List<NvgWidget> widgets() {
        List<NvgWidget> all = chips();
        for (ConfigRows.Row row : rowsModel.all()) {
            if (!row.isHeader()) {
                all.add(row.widget());
            }
        }
        return all;
    }

    /** 分段按钮（标签 + 样例）：底部那句说明与点击都要能找到它们。 */
    private List<NvgWidget> chips() {
        List<NvgWidget> all = new ArrayList<>(tabButtons);
        all.addAll(sampleButtons);
        return all;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UiTree probe = trellisProbe();
        // 【行内控件：命中由树说了算】controlRowAt 读的就是 Component.bounds()，
        // 跟悬停底、标签、说明共用一份几何（判据 1）。滚出视口的行不再需要 host 那套
        // "鼠标 y 在不在视口里"的补丁：视口外的盒子与指针不相交，树自己就判不到。
        boolean onControl = button == 0
                && TrellisBridge.controlRowAt(probe, (float) mouseX, (float) mouseY) >= 0;
        if (button == 0) {
            // 标签那一半也会收到按下（叠加层让"按下去了"看得见），只是那一格没有行为
            probe.pointerDown((float) mouseX, (float) mouseY);
        }
        // 【点预览 = 放一张】动画页来一张新的走完整时间线；其他页重播一次入场。
        // 【位置页例外】预览这时是整屏缩影 —— 点它直接开拖拽编辑场：在缩略图上看到
        // 位置不满意，下一步动作必然是"去调位置"，给他一步到位。
        ConfigLayout.Rect previewArea = layout().previewCard();
        if (layout().previewVisible()
                && mouseX >= previewArea.x() && mouseX < previewArea.right()
                && mouseY >= previewArea.y() && mouseY < previewArea.bottom()) {
            if (page == ConfigPageSpec.Page.LAYOUT) {
                openAnchorEditor();
            } else {
                preview.playOnce(now, sample);
            }
            return true;
        }
        if (onControl) {
            return true;        // 树已经把它按下去了，控件也收到 press 了
        }
        for (NvgWidget w : chips()) {
            // 树外的控件（标签列、切样例按钮）：命中还是自己判 —— 它们不在树的几何里，
            // 几何来自排布者那一份（{@code chipBoxes}），按下/松开/悬停用的是同一个矩形。
            Rect box = chipBoxes.get(w);
            if (box != null && box.contains((float) mouseX, (float) mouseY)
                    && w.press(box, mouseX, mouseY, button)) {
                return true;
            }
        }
        for (NvgWidget w : widgets()) {
            w.blur();       // 点在空白处：所有控件交还焦点（文本框的光标就该停）
        }
        probe.requestFocus(null);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 行内控件：树把抬起<b>发给按下的那一个</b>（指针捕获）—— 拖到格子外面松手，
        // 控件也能把手感收回去；落点还在格子里才算一次点击（见 ControlSlot）。
        trellisProbe().pointerUp((float) mouseX, (float) mouseY);
        for (NvgWidget w : chips()) {
            // 树外的控件：命中还是自己判，用的是排布者那一份几何（见 mouseClicked）
            Rect box = chipBoxes.get(w);
            if (box != null) {
                w.release(box, mouseX, mouseY, box.contains((float) mouseX, (float) mouseY));
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 拖拽只发给"正被按着"的那一个（A-10 第二步）。而"谁被按着"和"它的盒子在哪"都问树 ——
        // 控件不再存几何，所以盒子必须由这里交进去（与 press 收到的是同一个对象）。
        UiTree probe = trellisProbe();
        int row = TrellisBridge.pressedControlRow(probe);
        if (row >= 0) {
            Rect box = TrellisBridge.controlBox(probe, row);
            ConfigRows.Row r = rowsModel.all().get(row);
            if (box != null && !r.isHeader()) {
                r.widget().drag(box, mouseX, mouseY);
            }
        }
        return true;
    }

    // 【为什么没有 mouseMoved】1.20.1 的 GuiEventListener 没这个方法（它是后加的），
    // 而"鼠标在哪"每帧都要知道 —— 悬停状态统一在 render() 开头按当前鼠标位置刷。

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (itemsScroll == null) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        // 一格滚三行（按行不按像素：跨缩放档手感一致，见 ScrollMath）
        itemsScroll.wheel(delta, itemsContentHeight(), ConfigRows.ROW_STEP * 3f);
        layoutRows();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        trellisProbe().keyDown(keyCode);      // 键盘跟着焦点走，宿主照样先收（返回值不动宿主）
        for (NvgWidget w : widgets()) {
            if (w.keyPressed(keyCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        trellisProbe().charTyped(codePoint);
        for (NvgWidget w : widgets()) {
            if (w.charTyped(codePoint)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    // ------------------------------------------------------------------
    // 给 dev harness 的只读入口（不是给渲染层用的）
    // ------------------------------------------------------------------

    /** 按选项名找控件（含标签与样例按钮）。找不到返回 null —— 调用方要报，不能静默点空。 */
    public NvgWidget widgetFor(String label) {
        for (NvgWidget b : chips()) {
            if (b.label().equals(label)) {
                return b;
            }
        }
        for (ConfigRows.Row row : rowsModel.all()) {
            if (!row.isHeader() && row.label().equals(label)) {
                return row.widget();
            }
        }
        return null;
    }

    /** 走真实事件路径点一下某个选项（按下 → 松开），返回是否点到。 */
    public boolean clickOption(String label) {
        NvgWidget w = widgetFor(label);
        if (w == null) {
            return false;
        }
        Rect box = boxOf(w);
        // 【为什么零尺寸算点不到】预览收起时切样例按钮是零矩形（没画出来）。
        if (box == null || box.width() <= 0f || box.height() <= 0f) {
            return false;
        }
        double cx = box.x() + box.width() / 2.0;
        double cy = box.y() + box.height() / 2.0;
        boolean down = mouseClicked(cx, cy, 0);
        mouseReleased(cx, cy, 0);
        // 【诊断常驻】"点了没反应"只能靠这行定案：坐标对不对、按下命中没、点完屏幕换没换
        PickupCard.LOGGER.info("[clickOption] 『{}』点({},{}) 按下命中={} 控件 {}x{}@({},{}) 屏幕={}",
                label, Math.round(cx), Math.round(cy), down,
                Math.round(box.width()), Math.round(box.height()),
                Math.round(box.x()), Math.round(box.y()),
                Minecraft.getInstance().screen == null ? "无"
                        : Minecraft.getInstance().screen.getClass().getSimpleName());
        return true;
    }

    /**
     * 某个控件的几何：<b>行内控件问树，树外控件问排布者</b>（{@code chipBoxes}）。
     *
     * <p>【为什么这个方法值得单独存在】A-10 第二步把控件自己那份 {@code x/y/w/h} 删了，
     * 于是"这个控件在哪"只剩两个出处：树的 {@code bounds()}，和排布者给树外控件的那一份。
     * harness 的 {@code clickOption} / {@code dragOption} / {@code optionDump} 全走它 ——
     * 三条读数与绘制必然同一个矩形，不存在"日志说在这、画在别处"。
     */
    private Rect boxOf(NvgWidget widget) {
        if (chipBoxes.containsKey(widget)) {
            return chipBoxes.get(widget);
        }
        List<ConfigRows.Row> rows = rowsModel.all();
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).isHeader() && rows.get(i).widget() == widget) {
                return TrellisBridge.controlBox(trellisProbe(), i);
            }
        }
        return null;
    }

    /** 走真实事件路径拖一下滑条（按下 → 拖到 ratio 处 → 松开）—— 验的是拖拽，不是点击。 */
    public boolean dragOption(String label, double ratio) {
        NvgWidget w = widgetFor(label);
        if (w == null) {
            return false;
        }
        Rect box = boxOf(w);
        if (box == null) {
            return false;
        }
        double y = box.y() + box.height() / 2.0;
        double startX = box.x() + 2.0;
        double endX = box.x() + 2.0 + Math.max(0.0, Math.min(1.0, ratio)) * (box.width() - 4.0);
        boolean down = mouseClicked(startX, y, 0);
        mouseDragged(endX, y, 0, endX - startX, 0);
        mouseReleased(endX, y, 0);
        PickupCard.LOGGER.info("[dragOption] 『{}』按({},{})→拖({},{}) 按下命中={} 控件 {}x{}@({},{})",
                label, Math.round(startX), Math.round(y), Math.round(endX), Math.round(y), down,
                Math.round(box.width()), Math.round(box.height()),
                Math.round(box.x()), Math.round(box.y()));
        return true;
    }

    /** 走真实事件路径往某个文本框里打字并回车（点一下拿焦点 → 逐字 → 回车提交）。 */
    public boolean typeOption(String label, String text) {
        if (!clickOption(label)) {
            return false;
        }
        for (char c : text.toCharArray()) {
            charTyped(c, 0);
        }
        keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        return true;
    }

    /** 当前这一页有哪些选项（按玩家看到的顺序；小节头不是选项，不列）。日志里留一份。 */
    public List<String> optionLabels() {
        return rowsModel.all().stream().filter(r -> !r.isHeader()).map(ConfigRows.Row::label).toList();
    }

    /** 选项名 + 位置 + 显示值。布局是算出来的，"框压到边上了"必须能不靠眼睛查出来。 */
    public List<String> optionDump() {
        return rowsModel.all().stream().filter(r -> !r.isHeader()).map(r -> {
            NvgWidget w = r.widget();
            Rect box = boxOf(w);        // 几何只有树（行内）/ 排布者（树外）这一个出处
            return r.label() + "=" + Math.round(box.x()) + "," + Math.round(box.y())
                    + " " + Math.round(box.width()) + "x" + Math.round(box.height())
                    + " 值[" + w.value() + "]";
        }).toList();
    }

    // ------------------------------------------------------------------
    // 几何：按画布算，不写死
    // ------------------------------------------------------------------

    /** 标签左缘：配置列左边留 6px。 */
    private int labelX() {
        return ConfigRows.labelX(layout());
    }


    private int rowsTop() {
        // 预览已经搬到右边那一列了，配置项从这一列的顶上开始。
        // 那 2px 是行模型自己的几何（ConfigRows.ROWS_TOP_INSET）—— Trellis 试点取同一个源。
        return Math.round(layout().items().y()) + ConfigRows.ROWS_TOP_INSET;
    }

    // ------------------------------------------------------------------
    // 「位置」行
    // ------------------------------------------------------------------

    /** 「位置」那颗钮的值：一句话说清现在是自动还是自定义（自动 = 右下贴 HUD 带）。 */
    private String anchorValueText() {
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        if (layout.anchorX() < 0 && layout.anchorY() < 0) {
            return I18n.get("pickupcard.config.anchor.value.auto");
        }
        return I18n.get("pickupcard.config.anchor.value.custom",
                Math.round(layout.anchorLeft(this.width)),
                Math.round(layout.anchorTop(this.height,
                        CardStage.INSTANCE.previewStyle().boxHeight() * PreviewStage.scale(),
                        HudSafeZone.bottomInset())));
    }

    /** 打开整屏拖拽编辑场。配置不在这里写 —— 编辑场「完成」时才写。 */
    private void openAnchorEditor() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new AnchorEditScreen(this));
        }
    }
}
