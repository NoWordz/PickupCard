package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.CardMove;
import com.niuqu.pickupcard.layout.HudSafeZone;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 渲染层的调度台：把账本事件变成屏幕上的卡，每帧把它们交给 {@link NvgCardPainter}。
 * <p>
 * 【它不做什么】这里<b>没有一笔绘制</b>，也<b>没有一条几何公式</b>：
 * <ul>
 *   <li>怎么画 → {@link NvgCardPainter}（全项目唯一的画法）</li>
 *   <li>卡多大 → {@link CardMetrics}</li>
 *   <li>卡在哪 → {@code shared} 里的 {@link StackLayout}</li>
 *   <li>主题从哪来 → {@link StyleSource}</li>
 *   <li>动画进度 → {@link CardCanvas} / {@link CardTimeline}</li>
 * </ul>
 * 它只剩两件必须在一处才能保持正确的事：<b>消费事件</b>与<b>驱动每帧</b>。
 * <p>
 * 【为什么事件消费与每帧推进必须同处】账本"已经删掉了这张卡"与渲染层"该播退场了"
 * 是同一件事的两面，分到两个类里就会出现"A 删了 B 还不知道"的时序空窗。
 * <p>
 * 【2026-09-19 的三条新账】① 几何放不下不再硬切摘卡，改退回排队（[退回排队]）——
 * 从前拾取在这里无声蒸发，连排队都不进；② 自动缩放与卡宽都走 {@link CardMove} 平滑 ——
 * 缩放从前是按张数一档一档跳的阶梯，退场播完那一刻全摞卡瞬间放大一圈，正是用户报的
 * "动画结束时的图标回弹"；③ 几何容量每帧回报给账本（{@link Inbox#setGeometryCapacity}），
 * 放不下的拾取根本不上屏，在队列里等位子。
 */
public final class CardStage {

    public static final CardStage INSTANCE = new CardStage();

    /** 距屏幕左边的留白（右缘对齐时也就是右边距）。 */
    public static final int MARGIN_X = 16;
    /**
     * 距屏幕<b>下边</b>的留白 —— <b>不再是常量</b>，由 {@link HudSafeZone#bottomInset()} 算出来。
     * <p>
     * 【这里踩过三次】前两次都在改一个魔数（16 → 52），每次都说"这次好了"，结果还是重叠：
     * 第一次漏了血量/护甲那一带，第二次漏了<b>手持物品名</b>那一行（{@code Gui#renderSelectedItemName}
     * 里 {@code y = screenHeight - 59}、<b>居中</b>、宽度随物品名变化）—— 它会横伸进右列，
     * 而 52 让最下面那张卡正好落在 H-52..H-72，整行相交。
     * <p>
     * 现在这个数从原版 HUD 的矩形推出来（数字与出处都在 {@link HudSafeZone}）：
     * <b>让到"动作栏提示语"那一块之上</b>，就等于让开了它下面所有行。
     */
    /** 插入序 = 从老到新，正好是排布要的顺序。 */
    private final Map<String, CardView> live = new LinkedHashMap<>();

    /** 换位置时的过渡（旧的被新卡顶上去）。纯逻辑在 shared 里，有已知答案钉着。 */
    private final CardMove move = new CardMove();
    /**
     * 自动缩放与卡宽的过渡：同一个类、另两本账（key 固定 {@link #SCALE_KEY} / 各卡的 key）。
     * 起跑在原位、终点不跳、340ms 走完 —— 和 y 共用同一条曲线，三种量一起动才不像"分层错位"。
     */
    private final CardMove scaleMove = new CardMove();
    private final CardMove widthMove = new CardMove();
    private final List<Inbox.Event> pending = new ArrayList<>();
    private final StyleSource styles = new StyleSource();

    /**
     * 画法。**全项目只有这一个实现**（NanoVG 矢量）—— 2026-09-17 把 SDF 图层、原版整卡
     * 渲染、HTML 卡面当贴图那三条一起删了，理由（以及"内容为什么还在原版"）见
     * {@link NvgCardPainter} 的类注释。
     */
    private final NvgCardPainter painter = new NvgCardPainter();

    /** 布局设置来自 TOML；默认值让渲染层在没有 Forge 的情况下也能跑。 */
    private Supplier<LayoutSettings> layoutSource = LayoutSettings::defaults;

    /** 上一帧的排布结果与耗时，只给 harness 读。 */
    private List<CardSlot> lastSlots = List.of();
    private long layoutMicros;
    /** 上一帧绘制（NanoVG 外壳 + 图标现渲 + 文字）的耗时，给 harness 读 —— 性能护栏的另一半。 */
    private long paintMicros;
    /**
     * 这一轮卡堆里最慢的一帧：<b>帧号</b>、layout/paint 拆分、卡数、批次提交数，以及本轮
     * 单帧最多的提交次数（后者跟"最慢那一帧"不一定同时发生 —— 合并滚动会临时多提交两次/卡）。
     * <p>【为什么不能只看当前帧】{@code stats()} 是抽样读的（harness 在某 tick 顺手问一次），
     * 而真正卡的那一下往往落在入场/退场的中段 —— 抽样恰好命中它纯属运气。留住峰值后，
     * 事后读到的就是"这一轮里最糟的一帧长什么样"。
     * <p>【为什么帧号是必须的】只有 us 数是<b>认不出帧</b>的：A-25 遗留①就是拿着一个 15ms 的
     * worst 猜不出它落在入场动画的哪一段（记账与教训见 {@link FramePeak}）。
     */
    private final FramePeak peak = new FramePeak();
    /** 上一帧原版批次提交次数（{@link BatchStats} 的差值）—— 入场期它是帧开销的主要变量。 */
    private long lastFlushes;
    /** 上一帧主题里的入场时长与最新那张卡的展开进度，给 harness 读 —— 动画出问题时靠它定位。 */
    private long lastEnterMs;
    private float lastFirstRise = 1f;

    private CardStage() {
    }

    /**
     * 平台侧把主题与"玩家改过的外观项"接进来。配置界面改的就是这两样 ——
     * 界面不直接碰渲染，只改配置，渲染每秒重读一次。
     */
    public void setStyleSources(java.util.function.Supplier<com.niuqu.pickupcard.style.Theme> theme,
                                java.util.function.Supplier<com.niuqu.pickupcard.style.StyleOverrides> overrides) {
        styles.setThemeSource(theme);
        styles.setOverrideSource(overrides);
    }

    /** 配置界面预览用：当前生效的样式（主题 + 玩家改动过的项）。 */
    public StyleModel previewStyle() {
        return styles.current(System.currentTimeMillis());
    }

    /** 配置改完之后叫一下：下一次绘制立刻用新值，不用等那一秒的重读间隔。 */
    public void refreshStyle() {
        styles.invalidate();
    }

    /** 平台侧把布局配置接进来。 */
    public void setLayoutSource(Supplier<LayoutSettings> source) {
        this.layoutSource = source == null ? LayoutSettings::defaults : source;
    }

    /** 换世界/退出：屏上的卡、没消费的事件、缓存的主题一起清。与 {@link Inbox#reset()} 成对调用。 */
    public void clear() {
        live.clear();
        pending.clear();
        styles.invalidate();
        endRound();
        layoutMicros = 0L;
        paintMicros = 0L;
        retainMoves(Set.of());
    }

    // ------------------------------------------------------------------
    // Forge 总线
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // 只在这里取事件：拾取发生在包处理时，而卡的生命周期按 tick 走。
        pending.addAll(Inbox.INSTANCE.tick());
    }

    @SubscribeEvent
    public void onHudRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || suspended) return;
        // 【进世界后的前几帧：把引擎的账分摊付掉】见 NvgCardPainter#warmUpStep —— 这笔钱
        // 从前记在"第一次拾取"那一帧上（实测 42ms：外壳 23ms + 图标 27ms，用户实例里第一张
        // 卡同样要 51ms），玩家读到的就是"一捡东西就卡一下"。挪到进世界那几帧、每帧只付
        // 一小段（图标两个一组），那时世界还在加载区块，没人察觉得出这几帧的差别。
        if (mc.level != null) {
            if (warming) {
                StyleModel warmStyle = styles.current(System.currentTimeMillis());
                warming = NvgCardPainter.warmUpStep(event.getGuiGraphics(), warmStyle);
            } else if (!textWarmed) {
                // 【与引擎预热差开一帧】引擎预热的首帧本来就要 30ms 上下，文本这一笔再叠上去
                // 只会让那一帧更长。它自己占紧随其后的一帧（那时世界还在加载区块）。
                textWarmed = true;
                warmTextAndLayout(event.getGuiGraphics(),
                        styles.current(System.currentTimeMillis()), mc.font);
            }
        }
        renderInto(event.getGuiGraphics(), mc);
    }

    /** 引擎预热还没走完（进世界后的头几帧分摊付账；每进程一次）。 */
    private boolean warming = true;
    /** 文本与排布那一路的预热付过了没有（紧跟引擎预热之后的那一帧做）。 */
    private boolean textWarmed;

    /**
     * 拖拽编辑场打开时挂起真卡：屏幕上只能有一摞卡，编辑场的样例堆和真卡叠在一起分不清谁是谁。
     * <p>【为什么是挂起而不是清空】清空会把屏上的卡真删掉（账本一起忘），
     * 关掉编辑场时玩家就丢了那几条提示；挂起只是"这几帧不画"，背后一切照旧。
     */
    public void setSuspended(boolean value) {
        suspended = value;
        if (value) {
            lastSlots = List.of();
        }
    }

    /** 编辑场挂起是否生效中（只读，给诊断日志）。 */
    public boolean suspended() {
        return suspended;
    }

    private boolean suspended;

    /**
     * 消费事件 → 排布 → 交给 painter。**HUD 与 dev harness 共用这一条路径。**
     * <p>
     * 【为什么抽出来而不是让 harness 自己画一遍】harness 的全部价值在于它看到的东西
     * 与玩家看到的是同一份。如果 harness 自己走一条渲染路径，它就只能证明"那条路径"对，
     * 而上一版正是死在"harness 里没有的东西上了真机才现形"。
     * <p>
     * 【一帧的顺序（2026-09-19 重排）】摘退场播完的 → 量几何（锚线/卡高/容量）→
     * 放不下的退回排队 → 算自动缩放并平滑 → 排布 → 画。<b>缩放必须在摘卡之后算</b>：
     * 从前 place() 用摘卡前的张数定缩放，摘完的下一帧才跳到新档 —— "动画结束图标回弹"
     * 就是那慢一拍的跳变。
     */
    public void renderInto(GuiGraphics gui, Minecraft mc) {
        long now = System.currentTimeMillis();
        // 【为什么先取再 pump】pump 会把账本事件变成屏幕上的卡，而"这次合并该救回还是该重播入场"
        // 要用到 exitMs 与 reviveMs —— 事件处理拿不到它们，判据就只能靠猜。
        PickupCardSettings settings = Inbox.INSTANCE.settingsSnapshot();
        // 【不再每帧夹一次】{@link StyleSource#current} 保证出门的值一律夹过（玩家手写的数字在
        // 读进来那一刻就定死了）—— 从前这里每帧白扔一个 22 分量的 record（2026-09-20 性能轮）。
        StyleModel style = styles.current(now);
        pump(now, settings, style);

        // 【总开关】关掉就整条路都不走：屏上的卡立刻清、账本里的也一起忘掉。
        // 只"不再新弹"是不够的 —— 重新打开时那一堆旧卡会一起涌出来，像卡了半分钟。
        if (!settings.enabled()) {
            if (!live.isEmpty() || !pending.isEmpty()) {
                live.clear();
                pending.clear();
                Inbox.INSTANCE.reset();
                endRound();
            }
            return;
        }

        // 退场播完的摘掉，剩下的才参与排布
        live.values().removeIf(view -> {
            boolean done = view.exiting()
                    && CardTimeline.exit(now, view.exitStartAt(), settings.exitMs()) >= 1f;
            if (done) {
                Inbox.INSTANCE.forgetLeft(view.key());
            }
            return done;
        });

        // 淡回播完的复位 —— 不做这一步的话 exitStartAt 还挂着，它下一次退场会从半路开始
        for (CardView view : live.values()) {
            if (view.reviving() && reviveProgress(view, style, now) >= 1f) {
                view.endRevive();
            }
        }
        if (live.isEmpty()) {
            endRound();
            return;
        }

        // ---- 几何三件套：锚线、卡高（未缩放）、几何容量。与排布共用同一组数。----
        LayoutSettings layout = layoutSource.get().sanitized();
        lastBottomMargin = HudSafeZone.bottomInset();
        float unscaledH = style.boxHeight();
        float gap = layout.separation();
        float anchorTop = layout.anchorTop(gui.guiHeight(), unscaledH, lastBottomMargin);
        int capacity = StackLayout.fittingCount(anchorTop, unscaledH, gap);
        Inbox.INSTANCE.setGeometryCapacity(capacity);

        // 放不下：最老的几张退回排队队头（不再硬切 —— 硬切 = 拾取无声蒸发）
        if (live.size() > capacity) {
            List<CardView> oldestFirst = new ArrayList<>(live.values());    // live 是老到新
            List<Notice<Inbox.Card>> back = new ArrayList<>();
            for (int i = 0; i < live.size() - capacity; i++) {
                back.add(oldestFirst.get(i).notice());
            }
            for (Notice<Inbox.Card> notice : back) {
                live.remove(notice.key());
            }
            Inbox.INSTANCE.requeue(back);
        }
        if (live.isEmpty()) {
            endRound();
            return;
        }

        // ---- 自动缩放：先按当前张数算目标，再走 340ms 平滑（阶梯跳变的除颤器）----
        float targetScale = layout.scale(anchorTop, unscaledH, live.size(), gap);
        float scale = scaleMove.y(SCALE_KEY, targetScale, now);

        // 【先比数值，再决定要不要格式化】从前每帧无条件 String.format 一遍（7 个参数 →
        // varargs 数组 + 装箱 + 格式化），只为和上一次的字符串比一比。落点只在换分辨率、
        // 改锚点、改缩放时才变，那些帧之外这一段是纯白付（2026-09-20 性能轮）。
        float noteLeft = layout.anchorLeft(gui.guiWidth());
        if (noteLeft != stripLeft || anchorTop != stripTop || gui.guiWidth() != stripW
                || gui.guiHeight() != stripH || scale != stripScale || capacity != stripCapacity
                || live.size() != stripCards) {
            stripLeft = noteLeft;
            stripTop = anchorTop;
            stripW = gui.guiWidth();
            stripH = gui.guiHeight();
            stripScale = scale;
            stripCapacity = capacity;
            stripCards = live.size();
            stripNote = fmt("锚线 (%.0f,%.0f) 画布 %dx%d；缩放 %.0f%%；锚线上可用高 %.0fpx（容量 %d 张，屏上 %d 张）",
                    noteLeft, anchorTop, gui.guiWidth(), gui.guiHeight(),
                    scale * 100f, anchorTop, capacity, live.size());
            PickupCard.LOGGER.info("[落点] {}", stripNote);
        }

        CardCanvas canvas = canvas(now, style, settings, layout, gui, scale);
        lastEnterMs = style.enterMs();
        peakEnterWindowMs = Math.max(200L, style.enterMs()) + 200L;
        lastFirstRise = live.isEmpty() ? 1f : canvas.contentOf(live.values().iterator().next());

        long t0 = System.nanoTime();
        List<CardSlot> slots = layout(gui, mc, canvas, now, gap, anchorTop);
        layoutMicros = (System.nanoTime() - t0) / 1_000L;
        lastSlots = List.copyOf(slots);
        long flushesBefore = BatchStats.flushes();
        long t1 = System.nanoTime();
        painter.paint(gui, canvas, slots);
        paintMicros = (System.nanoTime() - t1) / 1_000L;
        lastFlushes = BatchStats.flushes() - flushesBefore;
        recordPeak();
    }

    /**
     * 预热：<b>文本与排布那一路</b>。进世界后紧跟引擎预热的那一帧调一次（每进程一次）。
     * <p>
     * 【为什么必须有它】第一张卡上屏那一帧实测 layout 要 4.0~4.6ms，而把"显示物品名"整个
     * 关掉，这 4ms 一分不少 —— 它不是名字的钱，是"第一次"的钱：{@code ItemStack#getHoverName()}
     * 头一次把 Component / 翻译模板 / 正则那几条路牵起来，{@code StackLayout} 与
     * {@code HudSafeZone} 头一次被摸到（类加载 + JIT 预热）。引擎那半边（NanoVG 首次真正绘制、
     * 图标首次烘焙）已经由 {@link NvgCardPainter#warmUpStep} 付掉了，这一半从前没人管 ——
     * 账单于是落在玩家第一次捡到东西的那一帧上（用户报的"一捡东西卡一下"）。
     * <p>
     * 【为什么用真卡而不是空跑公式】"第一次"由被调用的那段代码说了算：只有照真实顺序走一遍
     * （造 Notice → 造 CardView → 量宽 → 截名 → 屏幕外画一次字 → 排一次布 → 过一次让位判据），
     * 那几条路才会真的被牵起来。这与引擎预热踩过的三个坑是同一条教训。
     */
    private void warmTextAndLayout(GuiGraphics gui, StyleModel style, Font font) {
        try {
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
            Inbox.Card card = new Inbox.Card(
                    new com.niuqu.pickupcard.pickup.CardContent.Item(stack), false);
            long now = System.currentTimeMillis();
            Notice<Inbox.Card> notice = new Notice<>("~warm", "~warm", card, 64, false, now, now, 0);
            CardView view = new CardView(notice);
            CardCanvas canvas = new CardCanvas(now,
                    new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                            style.bumpEnabled()),
                    style, PickupCardSettings.defaults(), LayoutSettings.defaults(),
                    gui.guiWidth(), gui.guiHeight(), 1f);
            // 名字 + 数量 + 截断（走的是真卡那一份缓存）
            float width = CardMetrics.naturalWidth(canvas, font, view);
            float height = CardMetrics.height(canvas, font);
            // 文字：屏幕外画一次 —— 字形按需栅格化、图集上传、文本批次的第一次都记在这一笔
            gui.drawString(font, view.text().fitted(), -20_000, -20_000, style.nameColor(), true);
            // 排布与让位：纯数学，但第一次要付类加载
            List<StackLayout.Size> sizes = List.of(new StackLayout.Size(width, height));
            List<StackLayout.Slot> slots = StackLayout.stack(sizes, gui.guiWidth(), gui.guiHeight(),
                    LayoutSettings.defaults(), MARGIN_X, HudSafeZone.bottomInset(), style.gap());
            if (!slots.isEmpty()) {
                StackLayout.Slot slot = slots.get(0);
                HudSafeZone.reserve(new HudSafeZone.Rect(slot.x(), slot.y(), slot.width(),
                        slot.height()), null, 0f, null, 0f);
                HudSafeZone.shiftLeft(slot.x(), slot.width(), gui.guiWidth(), 0f);
            }
            PickupCard.LOGGER.info("[预热] 文本与排布那一路也付掉了（{}ms）：名字与数量度量、"
                            + "字形按需栅格化与图集上传、一次真实排布与让位判据",
                    System.currentTimeMillis() - now);
        } catch (Exception e) {
            // 预热失败不影响任何真功能：它只是提前付账，付不掉就照旧在第一次拾取时付。
            PickupCard.LOGGER.warn("[预热] 文本/排布那一路没热成（功能不受影响，只是那笔账晚点付）", e);
        }
    }

    /**
     * 收掉这一轮：屏上没卡了（或被总开关清掉）。峰值账本<b>不在这一刻清</b>，
     * 只挂个"等下一轮第一帧再清"的旗 —— 见 {@link #roundPendingReset}。
     * <p>【为什么由这条路径负责】峰值是「这一轮卡堆里最糟的一帧」，而"一轮结束"这件事
     * 只在<b>卡堆变空的那些帧</b>上看得见；它们都在 {@link #renderInto} 里提前 return，
     * 走不到 {@link #recordPeak}。从前峰值只在 recordPeak 内部的空槽分支上复位 ——
     * 那条分支在普通的"卡清空了"路径上根本到不了，于是峰值实际成了<b>整个进程</b>的极值
     * （名实不符，见 {@link FramePeak} 的类注释）。
     */
    private void endRound() {
        lastSlots = List.of();
        roundPendingReset = true;
    }

    /**
     * 卡堆已清空、等下一轮第一帧再复位峰值账本。
     * <p>【为什么延迟，而不是在 {@link #endRound} 里当场清】当场清的话，读数就<b>只在一轮
     * 还活着的时候</b>才拿得到：harness 是<b>抽样</b>读的（一个 tick 问一次），若卡堆在
     * 抽样之前就清空了，读到的是全 0，护栏那条 {@code peakCards() >= 4} 直接不成立、
     * 静默失效 —— 而那正是这一轮要修的"认不出帧"的反面（"干脆什么都看不到"）。
     * 延迟到下一轮首帧再清，刚结束那一轮的读数就还能被读一次。
     */
    private boolean roundPendingReset;

    /**
     * 把这一帧记进这一轮的峰值账。复位由 {@link #endRound} 挂旗、在这里兑现。
     * <p>只看"真有卡在屏上"的帧 —— 那条判据在 {@link FramePeak#observe} 里守着，这里不重复。
     */
    private void recordPeak() {
        if (roundPendingReset) {
            peak.reset();
            roundPendingReset = false;
        }
        long total = layoutMicros + paintMicros;
        peak.observe(total, layoutMicros, paintMicros, lastSlots.size(), lastFlushes,
                describeFrameShape());
        // 【慢帧探针】超线就留一条带上下文的日志：哪一帧、几张卡、提交几次、有没有卡在
        // 进场/退场。整帧只有几毫秒是常态，"哪一帧突然贵了"必须能事后指认 —— 上限 12 条，
        // 不刷屏；这条日志对玩家的下一次反馈同样有效（它就在正式版里）。
        // ⚠️ 这里的帧号与峰值账本的帧号同源（本帧 = 第 {@code peak.frames()} 帧）——
        // 有了它，"慢帧日志"与"峰值读数"才能被对到同一帧上。
        if (total > SLOW_FRAME_MICROS && slowFrameLogs < 12) {
            slowFrameLogs++;
            PickupCard.LOGGER.info("[慢帧] frame={} {}us（layout={} paint={} cards={} flushes={} {}）"
                            + "—— 阈值 {}us{}",
                    peak.frames(), total, layoutMicros, paintMicros, lastSlots.size(), lastFlushes,
                    describeFrameShape(), SLOW_FRAME_MICROS,
                    slowFrameLogs >= 12 ? "（本会话已报满 12 条）" : "");
        }
    }

    /** 这一帧的形态：几张在进场、几张在退场、主题缩放多少 —— 慢帧日志的上下文。 */
    private String describeFrameShape() {
        int entering = 0;
        int exiting = 0;
        for (CardSlot slot : lastSlots) {
            if (slot.view().exiting()) {
                exiting++;
            }
            if (slot.view().notice().bornAt() + peakEnterWindowMs > System.currentTimeMillis()) {
                entering++;
            }
        }
        return "进场=" + entering + " 退场=" + exiting;
    }

    /** 慢帧探针的阈值（微秒）：60fps 一帧 16667us，取 1/4 帧 —— 到这一线玩家已经能察觉。 */
    private static final long SLOW_FRAME_MICROS = 4_000L;
    private int slowFrameLogs;
    /** "还在进场"的判定窗口：主题入场时长 + 一点余量，只用于慢帧日志的上下文。 */
    private long peakEnterWindowMs = 1_000L;

    /** 消费积压的账本事件。 */
    private void pump(long now, PickupCardSettings settings, StyleModel style) {
        if (pending.isEmpty()) return;
        for (Inbox.Event e : pending) {
            absorb(e, now, settings, style);
        }
        pending.clear();
    }

    /**
     * 淡到多不透明以下就不再"救回"，改当新卡重播入场。
     * <p>【判据从哪来】用户 2026-09-18 报「卡片淡出的最后一帧，文字和图标还是会突然闪一下」。
     * 逐帧量下来它的成因是：救回的不透明度从"已经淡到哪儿"补回来，而在淡出末尾那个位置是
     * <b>alpha ≈ 0.01</b> —— 一张已经看不见的卡在 300ms 内冲回全不透明，屏幕上就是凭空冒出来
     * 一块，怎么调时长都还是闪。0.15 是"还看得出是同一张卡"的下限：低于它就等于换了一件东西，
     * 那本来就该是入场。
     */
    private static final float REVIVE_MIN_ALPHA = 0.15f;

    /** 淡回进度（≥1 = 补回完成）。Canvas 还没建的时候由这里现算，与 {@code CardCanvas#reviveOf} 同式。 */
    private static float reviveProgress(CardView view, StyleModel style, long now) {
        if (!view.reviving()) {
            return 0f;
        }
        long ms = style.reviveMs();
        return ms <= 0L ? 1f
                : com.niuqu.pickupcard.style.Easing.clamp01((now - view.reviveAt()) / (float) ms);
    }

    private CardCanvas canvas(long now, StyleModel style, PickupCardSettings settings,
                              LayoutSettings layout, GuiGraphics gui, float scale) {
        return new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                        style.bumpEnabled()),
                style, settings, layout,
                gui.guiWidth(), gui.guiHeight(), scale);
    }

    private static final String SCALE_KEY = "~scale";

    /**
     * 量尺寸 → 排布 → 把两边按序拼起来。
     * <p>【卡宽也走平滑】合并滚动里宽度按"旧值/新值里宽的"占位、滚完收回 —— 从前这一收一放
     * 是瞬间的，右缘对齐时整张卡左缘跳两下。{@code widthMove} 把它变成同一条 340ms 曲线。
     * <p>【让位矩形跟着底锚长】卡堆向上生长，占的地盘是 {@code [锚线-堆高, 锚线]}，
     * 侧栏/效果让位的判据矩形也按这个算。
     */
    private List<CardSlot> layout(GuiGraphics gui, Minecraft mc, CardCanvas canvas,
                                  long now, float gap, float anchorTop) {
        // 【index 0 = 最新】live 是插入序（老 -> 新），排布要的是新 -> 老（第 0 张贴着锚线），
        // 所以反转。
        List<CardView> alive = new ArrayList<>(live.values());
        Collections.reverse(alive);

        Font font = mc.font;
        float cardHeight = CardMetrics.height(canvas, font);

        List<StackLayout.Size> sizes = new ArrayList<>(alive.size());
        float widest = 0f;
        for (CardView view : alive) {
            float target = CardMetrics.width(canvas, font, view);
            float width = widthMove.y(view.notice().key(), target, now);
            widest = Math.max(widest, width);
            sizes.add(new StackLayout.Size(width, cardHeight));
        }

        // 右侧让位：<b>计分板侧栏</b>与<b>状态效果图标</b>，只在卡堆真的碰到时才让
        // （判据在 {@link HudSafeZone#reserve}，带单测）。
        float stackHeight = StackLayout.totalHeight(sizes, gap);
        float left = Math.max(0f, Math.min(canvas.layout().anchorLeft(canvas.guiWidth()),
                canvas.guiWidth() - MARGIN_X - widest));
        float reserve = rightReserve(mc, canvas, new HudSafeZone.Rect(left,
                anchorTop - stackHeight, widest, stackHeight));

        List<CardSlot> slots = new ArrayList<>(alive.size());
        for (StackLayout.Slot slot : StackLayout.stack(sizes, canvas.guiWidth(), canvas.guiHeight(),
                canvas.layout(), MARGIN_X, lastBottomMargin, gap)) {
            CardView view = alive.get(slot.index());
            float x = slot.x() - HudSafeZone.shiftLeft(slot.x(), slot.width(),
                    canvas.guiWidth(), reserve);
            slots.add(new CardSlot(view, x, move.y(view.notice().key(), slot.y(), now),
                    slot.width(), slot.height()));
        }
        retainMoves(live.keySet());
        return slots;
    }

    /** 三本过渡账一起剪枝：只有还在屏上的 key 才留。 */
    private void retainMoves(Set<String> liveKeys) {
        move.retain(liveKeys);
        widthMove.retain(liveKeys);
        Set<String> withScale = new HashSet<>(liveKeys);
        withScale.add(SCALE_KEY);
        scaleMove.retain(withScale);
    }

    /**
     * 上一帧画了哪些卡、量了多久。**只读遥测，没有写入口**——它存在是为了让 harness 能把
     * "看不见的状态"（每张卡的实际位置与尺寸）变成可读的，而不是为了让别处改渲染。
     * <p>峰值这一组带<b>帧号</b>（{@code peakFrameNo}，1 起）与本轮总帧数（{@code peakFrames}）：
     * 只给一个 us 数，读的人认不出那是入场动画的哪一帧（A-25 遗留①卡在这里）。
     * 另有最慢那一帧自己的 layout/paint 拆分 —— 只看合计分不清它贵在排布还是贵在绘制。
     * <p><b>@implNote 只允许渲染线程读。</b>峰值那几个数不是一个原子快照（{@link FramePeak}
     * 里逐字段读写、{@link #endRound} 的复位也要下一帧才兑现）—— 换线程读会拿到跨轮拼起来的
     * 读数（比如帧号还是上一轮、us 数已是这一轮）。今天唯一的调用方都在渲染线程上。
     */
    public record Stats(int live, int painted, long layoutMicros, long paintMicros,
                        long enterMs, float firstRise,
                        long flushes, long maxFlushes,
                        long peakFrameNo, long peakFrames, long peakMicros,
                        long peakLayoutMicros, long peakPaintMicros,
                        int peakCards, long peakFlushes, String peakShape) {
    }

    public Stats stats() {
        return new Stats(live.size(), lastSlots.size(), layoutMicros, paintMicros,
                lastEnterMs, lastFirstRise, lastFlushes, peak.maxFlushes(),
                peak.worstFrameNo(), peak.frames(), peak.worstMicros(),
                peak.worstLayoutMicros(), peak.worstPaintMicros(),
                peak.worstCards(), peak.worstFlushes(), peak.worstShape());
    }

    /** 上一帧参与绘制的卡。辅助线要按这个画，才保证画的是"真的画了的那批"。 */
    public List<CardSlot> lastSlots() {
        return lastSlots;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /** 账本事件 → 屏幕上的卡。 */
    private void absorb(Inbox.Event event, long now, PickupCardSettings settings, StyleModel style) {
        if (event instanceof Inbox.Event.Added added) {
            // 【为什么要记这一笔】同一个 key 又"新增"了一张，说明屏幕上那张（可能正在淡出）
            // 会被**整张换掉**：新 CardView 的 exitStartAt = NO_EXIT，不透明度瞬间回到 1。
            // 用户报的「淡出最后一帧完全不透明，然后消失」只可能是这一类事件造成的，
            // 所以把它写成日志，让下一次复现自己带上证据。
            if (live.put(added.notice().key(), new CardView(added.notice())) != null) {
                PickupCard.LOGGER.info("[重挂] key={}：同名卡被整张替换（可能在淡出中）",
                        added.notice().key());
            }
        } else if (event instanceof Inbox.Event.Merged merged) {
            CardView view = live.get(merged.notice().key());
            if (view == null) {
                // 时序兜底：账本说有、渲染却没见过，按新卡补挂
                live.put(merged.notice().key(), new CardView(merged.notice()));
            } else if (view.exiting()
                    && CardTimeline.exitAlpha(now, view.exitStartAt(), settings.exitMs())
                            < REVIVE_MIN_ALPHA) {
                // 已经淡到几乎看不见：救回来就是"从无到有"，补得再慢也读成闪。
                // 换一张新卡重播入场 —— 玩家按下拾取键的那一刻，本来就该看见"东西进来了"。
                live.put(view.key(), new CardView(merged.notice()));
                PickupCard.LOGGER.info("[救回→新卡] key={}：淡到 alpha<{} 才被再次拾起，改播入场",
                        merged.notice().key(), REVIVE_MIN_ALPHA);
            } else {
                if (view.exiting()) {
                    // 合并撤销退场，但**不是瞬间回到全不透明**：CardView#beginRevive 记下起点，
                    // 之后 reviveMs（主题 --pc-revive-ms）补回去。同样进日志。
                    PickupCard.LOGGER.info("[救回] key={}：淡出改播淡回（{}ms 补回全不透明）",
                            merged.notice().key(), style.reviveMs());
                }
                view.absorbMerge(merged.notice(), now);
            }
        } else if (event instanceof Inbox.Event.Evicted evicted) {
            beginExit(evicted.notice().key(), now);
        } else if (event instanceof Inbox.Event.Expired expired) {
            beginExit(expired.notice().key(), now);
        }
    }

    private void beginExit(String key, long now) {
        CardView view = live.get(key);
        if (view != null) {
            view.beginExit(now);
        }
    }

    /** 本帧生效的底部留白：锚线是"HUD 带上方"这条下界的上游（诊断日志用）。 */
    private int lastBottomMargin = HudSafeZone.bottomInset();
    /** 上一次落点变化时打过的日志（变了才打，不刷屏）。 */
    private String stripNote = "";
    /**
     * 上一次打过日志的落点数值。
     * <p>【为什么要存数】那行提示由 7 个参数拼出来，从前每帧先 {@code String.format} 一遍
     * （varargs 数组 + 装箱 + 格式化）再拿结果和上一次比 —— 纯白付。现在先比数值，
     * 全一样的话连格式化都不发生（2026-09-20 性能轮）。
     */
    private float stripLeft = Float.NaN;
    private float stripTop = Float.NaN;
    private float stripScale = Float.NaN;
    private int stripW = -1;
    private int stripH = -1;
    private int stripCapacity = -1;
    private int stripCards = -1;

    /** 本帧的落点说明（只读诊断；harness 那行"安全区"日志末尾会带上它）。 */
    public String placementNote() {
        return stripNote;
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(java.util.Locale.ROOT, pattern, args);
    }

    /**
     * 右侧要让开多少：<b>计分板侧栏</b>与<b>状态效果图标</b> —— 两者都只在卡堆<b>真的碰到</b>
     * 它们时才让（判据在 {@link HudSafeZone#reserve}，带单测）。
     * <p>
     * 【为什么每一帧现问】它们是"有时才在、而且在屏幕中部"的东西：让多少由它们自己决定，
     * 就不是又一个魔数。数字与出处见 {@link HudSafeZone}（图标一行 26 高、一列 25 宽；侧栏宽度现量）。
     * <p>
     * 【贴底之后撞得少了吗】状态效果图标在屏幕<b>右上</b>，卡堆贴<b>右下</b>—— 小画布上
     * 高堆（顶到锚线以上）仍可能碰上；侧栏在屏幕中部，堆高时一样要让。判据不变，照旧每帧现问。
     *
     * @param cards 卡堆这一帧可能占到的矩形（调用方按本帧真实的落点算，别在这里再算一遍）
     */
    private static float rightReserve(Minecraft mc, CardCanvas canvas, HudSafeZone.Rect cards) {
        if (mc.level == null || cards.h() <= 0f || cards.w() <= 0f) {
            return 0f;
        }

        // 1 = 侧栏槽位（原版 Gui#render 里就是这么取的：getDisplayObjective(1)）
        Objective sidebar = mc.level.getScoreboard().getDisplayObjective(1);
        HudSafeZone.Rect sidebarRect = null;
        float sidebarWidth = 0f;
        if (sidebar != null) {
            sidebarWidth = scoreboardWidth(mc.font, sidebar);
            sidebarRect = HudSafeZone.sidebar(canvas.guiWidth(), canvas.guiHeight(),
                    sidebarWidth, scoreboardLines(sidebar));
        }

        int beneficial = 0;
        int harmful = 0;
        if (mc.player != null) {
            for (var effect : mc.player.getActiveEffects()) {
                if (effect.getEffect().isBeneficial()) {
                    beneficial++;
                } else {
                    harmful++;
                }
            }
        }
        int columns = Math.max(beneficial, harmful);
        HudSafeZone.Rect effectRect = null;
        float effectWidth = 0f;
        if (columns > 0) {
            // 有害效果会再占一排（图标从 y=27 起）—— 所以图标带的下沿要看有没有那一排
            float effectsBottom = harmful > 0 ? 51f : 25f;
            effectWidth = HudSafeZone.EFFECT_COL_W * columns;
            effectRect = new HudSafeZone.Rect(canvas.guiWidth() - effectWidth, 1f,
                    effectWidth, effectsBottom - 1f);
        }

        return HudSafeZone.reserve(cards, sidebarRect, sidebarWidth, effectRect, effectWidth);
    }

    /** 侧栏会画几行（原版最多 15 行 —— 它决定那一竖条的纵向范围）。 */
    private static int scoreboardLines(Objective objective) {
        int lines = 0;
        for (Score ignored : objective.getScoreboard().getPlayerScores(objective)) {
            if (++lines >= 15) {
                break;
            }
        }
        return Math.max(1, lines);
    }

    /** 侧栏一行的实测宽度（原版是按"标题 / 条目+分数"的最宽那行算的，这里取个上界就够）。 */
    private static int scoreboardWidth(Font font, Objective objective) {
        int widest = font.width(objective.getDisplayName());
        int seen = 0;
        for (Score score : objective.getScoreboard().getPlayerScores(objective)) {
            if (++seen > 15) {
                break;      // 原版也只显示前 15 行
            }
            String owner = score.getOwner();
            if (owner == null || owner.startsWith("#")) {
                continue;
            }
            widest = Math.max(widest, font.width(owner) + 8 + font.width(Integer.toString(score.getScore())));
        }
        return widest;
    }

}
