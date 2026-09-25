package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.CardMove;
import com.niuqu.pickupcard.layout.HudSafeZone;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * 预览舞台：配置界面预览面板里的卡，与拖拽编辑场共用的样例来源。
 *
 * <p>【为什么独立成类（2026-09-19 架构审计）】预览管线从前长在 1700 行的界面类里，
 * 和真卡管线（{@code CardStage}）平行，最要命的是<b>卡片身份当场现编</b>：同一样例的
 * 所有卡共用一个 key，而"换位平滑"（{@code CardMove}）按 key 记账 —— 舞台上同时有两张
 * 同款卡时，它们每帧互相抢同一个过渡目标，稳态是<b>所有同 key 卡永远钉在同一个 y 上</b>，
 * 这就是用户报的"预览卡片重叠"。现在每张卡出生时领唯一的号（{@code preview#序号}），
 * 平滑账本一本账管一张卡，重叠在结构上不可能再发生。
 *
 * <p>【节拍为什么改成"三并一新"】从前自动节拍发现队首同款就只并数字 —— 舞台永远只有
 * 一张卡，"三张真卡的自动舞台"名不副实，退场只能靠手动点爆（用户报的"还没退出动画"）。
 * 现在每四拍并一次（演示合并脉冲 + 数字滚动），其余开新卡：容量到了自然有老卡退场，
 * 动画页要调的入场→停留→消失一整条链自己会演完。
 *
 * <p>【三种模式（2026-09-19 grill 定案）】
 * <ul>
 *   <li>{@link Mode#STAGE} —— 动画页：三张真卡的自动舞台，贴面板底向上长；</li>
 *   <li>{@link Mode#STATIC} —— 其余页：一张居中的静止完整卡，看"卡长什么样"；</li>
 *   <li>{@link Mode#MINIMAP} —— 位置页：<b>整屏等比缩影</b>。用户 grill 定案"预览要和
 *       游戏里的位置对得上"：整块屏幕按面板宽等比缩进预览（HUD 带画暗示线），卡按
 *       真实的锚点/对齐/同屏上限/缩放公式排在缩影里 —— 与游戏同一条
 *       {@link StackLayout#stack}，位置一眼对上。取舍已言明：缩影里的卡很小，
 *       但位置页要的就是位置感；看卡的长相去别的页（特写）。</li>
 * </ul>
 */
public final class PreviewStage {

    /** 预览模式，见类注释。 */
    public enum Mode {
        /** 动画页：自动舞台。 */
        STAGE,
        /** 默认：居中静止卡（特写）。 */
        STATIC,
        /** 位置页：整屏等比缩影。 */
        MINIMAP
    }

    /** 舞台节拍：每隔这么长时间来一拍（略放慢，看得清退场）。 */
    private static final long BEAT_MS = 1_700L;
    /** 开场后第一拍等多久。 */
    private static final long FIRST_BEAT_MS = 500L;
    /** 舞台同屏上限（不含正在退场的那张）。 */
    private static final int CAPACITY = 3;

    // ------------------------------------------------------------------
    // 样例：四张故意长得不一样的卡（预览与编辑场共用的一份内容）
    // ------------------------------------------------------------------

    /**
     * 【为什么不是一个固定的"经验卡"】排版问题只在特定内容下才露出来 —— 名字长到要截断、
     * 稀有度换颜色、微光、只有数字。给一个样例等于只验一种。
     * <p>【四格为什么正好是四个颜色】用户 2026-09-18 问过"预览里四个等级颜色都一样"——
     * 当年是样例物品选错了（钻石剑其实是 COMMON）。现在每格写死标称档位，
     * {@link #verify()} 启动时对一次，不符报 ERROR。
     */
    public enum Sample {
        COMMON("pickupcard.config.sample.common.label", Items.STONE, Rarity.COMMON, 64, false, null,
                "pickupcard.config.sample.common.hint"),
        RARE("pickupcard.config.sample.rare.label", Items.GOLDEN_APPLE, Rarity.RARE, 1, false, null,
                "pickupcard.config.sample.rare.hint"),
        XP("pickupcard.config.sample.xp.label", Items.NETHER_STAR, null, 137, true, null,
                "pickupcard.config.sample.xp.hint"),
        LONG_NAME("pickupcard.config.sample.longName.label", Items.ENCHANTED_GOLDEN_APPLE, Rarity.EPIC, 1, false,
                "pickupcard.config.sample.longName.custom", "pickupcard.config.sample.longName.hint");

        // 【存 key】同 ConfigPageSpec.Page：枚举初始化早于语言加载，文案在访问器里解析
        final String labelKey;
        /** 这一格的数量。<b>不是显示字符串</b> —— 写法由玩家选的 {@code CountFormat} 决定。 */
        final int amount;
        /** 值得给一层稀有度微光的卡（经验卡）。 */
        final boolean glow;
        final String hintKey;
        /** 这一格<b>标称</b>的稀有度；{@code null} = 不参与稀有度演示（经验卡）。 */
        private final Rarity tier;
        private final ItemStack icon;

        Sample(String labelKey, Item item, Rarity tier, int amount, boolean glow,
               String customName, String hintKey) {
            this.labelKey = labelKey;
            this.tier = tier;
            this.amount = amount;
            this.glow = glow;
            this.hintKey = hintKey;
            this.icon = new ItemStack(item);
            if (customName != null) {
                // 玩家自己改过名的物品就长这样：名字长、还带符号。
                // customName 是 lang key（枚举初始化早于语言加载，只能存 key 现解析）——
                // literal 会把 key 原文当卡名（2026-09-19 英文截图 "pickupcard.config.sample.l…"的真身）。
                this.icon.setHoverName(Component.translatable(customName));
            }
        }

        /**
         * 启动时对一次：标称档位必须就是<b>原版读出来的</b>那一档。只在第一次用到时跑。
         */
        static void verify() {
            if (verifyDone) {
                return;
            }
            verifyDone = true;
            for (Sample s : values()) {
                if (s.tier != null && s.icon.getRarity() != s.tier) {
                    PickupCard.LOGGER.error("[样例] {} 标称 {}，实际读到的是 {}（{}）—— 预览的颜色会不对",
                            s.label(), s.tier, s.icon.getRarity(),
                            BuiltInRegistries.ITEM.getKey(s.icon.getItem()));
                }
            }
        }

        private static boolean verifyDone;

        public String label() {
            return I18n.get(labelKey);
        }

        public String hint() {
            return I18n.get(hintKey);
        }

        public int amount() {
            return amount;
        }

        /** 样例物品（{@code XP} 那格的真卡内容不是物品）。 */
        ItemStack icon() {
            return icon;
        }
    }

    /** 预览缩放：手动档照玩家的选择画；自动档在特写里恒为 100%（倍率取决于真实卡堆，特写面板算它只会骗人）。 */
    public static float scale() {
        int pct = PickupCardConfig.layoutSnapshot().scalePercent();
        return pct > LayoutSettings.AUTO_SCALE ? pct / 100f : 1f;
    }

    /** 一张"早已出生"的卡：入场已播完、无退场计划 —— 静止页与编辑场都用它。 */
    public static CardView settledView(Sample s) {
        return new CardView(notice(nextKey(), s, s.amount, System.currentTimeMillis() - 10_000L));
    }

    private static Notice<Inbox.Card> notice(String key, Sample s, int amount, long bornAt) {
        Inbox.Card payload = new Inbox.Card(
                s == Sample.XP ? new CardContent.Experience() : new CardContent.Item(s.icon()),
                s.glow);
        return new Notice<>(key, "preview", payload, amount, false, bornAt, bornAt, 0);
    }

    // ------------------------------------------------------------------
    // 舞台本体
    // ------------------------------------------------------------------

    /** 舞台上的一位：卡 + 它是哪个样例（合并演示要按样例认亲，不能按 key —— key 每张唯一）。 */
    private record Entry(CardView view, Sample sample) {
    }

    private final java.util.ArrayDeque<Entry> stage = new java.util.ArrayDeque<>();
    private final CardMove move = new CardMove();
    private final NvgCardPainter painter = new NvgCardPainter();
    /** 特写模式的那张静止完整卡（{@code playOnce} 换新重生）。 */
    private CardView staticCard;
    private Mode mode = Mode.STATIC;
    /** 下一拍的时刻（{@code <0} = 还没开过场）。 */
    private long nextSpawnAt = -1L;
    private long beats;
    /** 身份发放计数：预览卡的 key 全项目唯一，平滑账本一本账只管一张卡。 */
    private static long seq;

    private static String nextKey() {
        return "preview#" + (++seq);
    }

    /**
     * 按页分工（2026-09-19 grill 定案）：动画页跑舞台、位置页上缩影、其余页静止特写。
     * 离开动画页收舞台；回来时重新开场（节拍从头起）。
     */
    public void setMode(Mode want, Sample sample) {
        if (want == Mode.STAGE) {
            if (mode != Mode.STAGE) {
                nextSpawnAt = -1L;
            }
        } else {
            stage.clear();
            move.retain(java.util.Set.of());
            staticCard = want == Mode.STATIC
                    ? new CardView(notice(nextKey(), sample, sample.amount,
                            System.currentTimeMillis() - 10_000L))
                    : null;      // 缩影不需要特写卡 —— 卡堆现场按真实公式排
        }
        mode = want;
    }

    /** 「来一张」/ 点预览：动画页放一张走完整时间线；特写页把静止卡换成当前样例；缩影页无事可做（卡堆是真实排布，没有"多来一张"）。 */
    public void playOnce(long now, Sample sample) {
        if (mode == Mode.STAGE) {
            // 手动钮必出新卡：合并演示交给自动节拍
            spawn(now, sample, false);
            nextSpawnAt = now + BEAT_MS;
        } else if (mode == Mode.STATIC) {
            staticCard = new CardView(notice(nextKey(), sample, sample.amount,
                    System.currentTimeMillis() - 10_000L));
        }
    }

    /** 舞台的节拍：到点来一拍，退场播完的清掉。只在舞台模式下动。 */
    public void drive(long now, Sample sample) {
        if (mode != Mode.STAGE) {
            return;
        }
        if (nextSpawnAt < 0L) {
            nextSpawnAt = now + FIRST_BEAT_MS;
        }
        if (now >= nextSpawnAt) {
            beats++;
            // 每四拍并一次（演示脉冲 + 数字滚动），其余开新卡 —— 见类注释
            spawn(now, sample, beats % 4 == 0);
            nextSpawnAt = now + BEAT_MS;
        }
        long exitMs = PickupCardConfig.snapshot().exitMs();
        stage.removeIf(entry -> entry.view().exiting()
                && CardTimeline.exit(now, entry.view().exitStartAt(), exitMs) >= 1f);
    }

    /**
     * 往舞台放一拍。
     *
     * @param mergeDemo true = 队首是同款且没在退场时并进去（数字滚动演示）；
     *                  false = 必出新卡（手动「来一张」用 —— 从前点它"没反应"就是被并吞了）
     */
    private void spawn(long now, Sample sample, boolean mergeDemo) {
        Entry front = stage.peekFirst();
        if (mergeDemo && front != null && !front.view().exiting() && front.sample() == sample) {
            // 合并必须沿用 front 的 key：key 一换，平滑账本就认不出这张卡，位置会跳
            front.view().absorbMerge(notice(front.view().key(), sample,
                    front.view().notice().count() + 1, now), now);
            return;
        }
        stage.addFirst(new Entry(new CardView(notice(nextKey(), sample, sample.amount, now)), sample));
        // 超员的退场：最老那张开始消失，播完由 drive 清掉。beginExit 幂等，重复调用无害。
        while (stage.size() > CAPACITY) {
            Entry oldest = stage.peekLast();
            if (!oldest.view().exiting()) {
                oldest.view().beginExit(now);
            }
            if (stage.size() > CAPACITY + 1) {
                stage.removeLast();     // 消失时长被设得很长时别让尸体堆着
            } else {
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // 缩影几何：render 与点击命中共用同一块矩形
    // ------------------------------------------------------------------

    /**
     * 位置页缩影的矩形：<b>整块屏幕等比缩进预览面板</b> —— 宽随面板，高按真实屏幕的
     * 宽高比折算，在面板里垂直居中。映射比 {@code ratio = 缩略宽 / 真实画布宽}：
     * 真实坐标 × ratio = 缩略坐标，锚点分数、HUD 带高、卡尺寸全部同一条映射。
     */
    public static ConfigLayout.Rect mapRect(ConfigLayout.Rect area, float canvasWidth, float canvasHeight) {
        float w = Math.max(1f, area.w());
        float h = w * canvasHeight / Math.max(1f, canvasWidth);
        float y = area.y() + Math.max(0f, (area.h() - h) / 2f);
        return new ConfigLayout.Rect(area.x(), y, w, h);
    }

    /**
     * 按 (x, y, 宽, 高) 画一块矩形。
     * <p>【为什么包一层】{@code GuiGraphics.fill} 的签名是<b>两个对角点</b> (x1,y1,x2,y2)
     * —— 当成 (x,y,w,h) 传的话，"宽高"被当成"右下角坐标"，1px 的边框线会画成横跨
     * 小半个屏幕的大色块（2026-09-19 缩影第一版的真事：底边框 fill 画出了 400×470
     * 物理px 的半透明灰块压在配置列上）。
     */
    private static void fillRect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        gui.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    /**
     * 画这一帧的预览（动画页 = 舞台整摞；位置页 = 整屏缩影；其余页 = 居中的静止卡）。
     *
     * @param area 预览面板里放卡的那块（{@code ConfigLayout#previewCard}）
     */
    public void render(GuiGraphics gui, ConfigLayout.Rect area,
                       float canvasWidth, float canvasHeight, StyleModel style, long now) {
        if (mode == Mode.STAGE) {
            renderStage(gui, area, canvasWidth, canvasHeight, style, now);
        } else if (mode == Mode.MINIMAP) {
            renderMinimap(gui, area, canvasWidth, canvasHeight, style, now);
        } else if (staticCard != null) {
            renderStatic(gui, area, canvasWidth, canvasHeight, style, now);
        }
    }

    /**
     * 静止样例卡：<b>在面板里居中</b>。从前贴面板底角，面板又高又空 —— 卡像放丢了；
     * 居中之后它是一张"标本"，不是一摞卡的替身。
     */
    private void renderStatic(net.minecraft.client.gui.GuiGraphics gui, ConfigLayout.Rect area,
                              float canvasWidth, float canvasHeight, StyleModel style, long now) {
        float scale = scale();
        PickupCardSettings settings = panelSettings(style, scale, area.w(), canvasWidth, canvasHeight, now);
        float h = style.boxHeight() * scale;
        float w = Math.min(cardWidth(staticCard, style, scale, settings, canvasWidth, canvasHeight, now),
                area.w() - 6f);
        float x = area.x() + (area.w() - w) / 2f;
        float y = area.y() + (area.h() - h) / 2f;
        painter.paint(gui, previewCanvas(style, scale, settings, canvasWidth, canvasHeight, now),
                List.of(new CardSlot(staticCard, x, y, w, h)));
    }

    /**
     * 位置页的整屏缩影。<b>卡堆与游戏同一条公式</b>：锚点分数、对齐档、同屏上限、
     * 自动缩放全部按真实值先算，再整体乘映射比落进缩略矩形 —— 缩略里卡停的地方
     * 就是游戏里卡停的地方。HUD 带画一条暗示带：它决定了自动锚线的位置，也解释
     * "为什么卡不能更低"。
     */
    private void renderMinimap(GuiGraphics gui, ConfigLayout.Rect area,
                               float canvasWidth, float canvasHeight, StyleModel style, long now) {
        ConfigLayout.Rect map = mapRect(area, canvasWidth, canvasHeight);
        float ratio = map.w() / Math.max(1f, canvasWidth);

        // 缩略屏的"身体"：暗底 + 一圈细边 —— 不画它，看不出来这是一块屏幕
        int frame = 0x5080A0B0;
        fillRect(gui, map.x(), map.y(), map.w(), 1, frame);
        fillRect(gui, map.x(), map.y() + map.h() - 1, map.w(), 1, frame);
        fillRect(gui, map.x(), map.y(), 1, map.h(), frame);
        fillRect(gui, map.x() + map.w() - 1, map.y(), 1, map.h(), frame);

        // HUD 带暗示：底部一条半透明带 + 上沿线。自动锚线就停在这条带之上 ——
        // 它是"卡为什么不能更低"的几何答案（高度按同一条 ratio 折算，位置诚实）。
        float insetPx = HudSafeZone.bottomInset() * ratio;
        fillRect(gui, map.x() + 1, map.y() + map.h() - insetPx, map.w() - 2, insetPx, 0x28FFFFFF);
        fillRect(gui, map.x() + 1, map.y() + map.h() - insetPx, map.w() - 2, 1, 0x50FFFFFF);

        // ---- 卡堆：先按真实坐标算（与游戏同一条公式），再整体乘 ratio 落进缩略 ----
        LayoutSettings lay = PickupCardConfig.layoutSnapshot().sanitized();
        PickupCardSettings real = PickupCardConfig.snapshot();
        var font = Minecraft.getInstance().font;
        float cardScale = scale();
        float unscaledH = style.boxHeight();
        float anchorTopReal = lay.anchorTop(canvasHeight, unscaledH, HudSafeZone.bottomInset());
        int count = Math.max(1, real.maxOnScreen());
        // 自动缩放按真实坐标算（"张数×卡高 vs 可用高"的比例式），缩略里照搬同一个倍率
        cardScale = lay.scale(anchorTopReal, unscaledH, count, lay.separation());
        // 真实容量裁剪：放不下的卡在游戏里也上不了屏（几何容量门）—— 缩影必须说同一句实话
        int capacity = StackLayout.fittingCount(anchorTopReal, unscaledH * cardScale, lay.separation());
        int visible = Math.min(count, capacity);
        if (visible <= 0) {
            return;     // 真实屏幕上一张都放不下（锚点被拖到极低）：缩影如实画空
        }

        PreviewStage.Sample[] samples = Sample.values();
        List<CardView> views = new ArrayList<>(visible);
        List<StackLayout.Size> sizes = new ArrayList<>(visible);
        float ratioScale = ratio * cardScale;
        for (int i = 0; i < visible; i++) {
            CardView view = settledView(samples[i % samples.length]);
            views.add(view);
            sizes.add(new StackLayout.Size(
                    CardMetrics.naturalWidth(
                            previewCanvas(style, cardScale, real, canvasWidth, canvasHeight, now),
                            font, view) * ratioScale,
                    unscaledH * ratioScale));
        }
        List<StackLayout.Slot> slots = StackLayout.stack(sizes, map.w(), map.h(), lay,
                Math.round(CardStage.MARGIN_X * ratio), Math.round(HudSafeZone.bottomInset() * ratio),
                lay.separation() * ratioScale);

        // 画：mini 画布的宽/高/缩放全用缩略坐标系的值 —— 卡宽上限（0.45 屏宽）随之同构，
        // 名字截断预算和真实屏幕是同一条公式算出来的
        CardCanvas mini = previewCanvas(style, cardScale * ratio, real,
                map.w(), map.h(), now);
        List<CardSlot> out = new ArrayList<>(views.size());
        for (int i = 0; i < views.size(); i++) {
            StackLayout.Slot s = slots.get(i);
            out.add(new CardSlot(views.get(i),
                    map.x() + s.x(), map.y() + s.y(), s.width(), s.height()));
        }
        painter.paint(gui, mini, out);
    }

    /**
     * 舞台落位：<b>贴面板底</b>（与真卡的底锚同构 —— 预览说"新卡出现在固定的一点"，它自己
     * 就得先做到），水平按锚线分数落位。堆里多张时向上生长；换位走 340ms 平滑，跟真卡一路。
     */
    private void renderStage(net.minecraft.client.gui.GuiGraphics gui, ConfigLayout.Rect area,
                             float canvasWidth, float canvasHeight, StyleModel style, long now) {
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        float scale = scale();
        float gap = layout.separation() * scale;
        // 【名字截断跟着面板走，不跟整屏走】卡壳被面板夹窄后，名字的截断预算若还按
        // "屏宽 × 45%" 算，文字就会溢出卡壳、戳出面板
        PickupCardSettings settings = panelSettings(style, scale, area.w(), canvasWidth, canvasHeight, now);
        float fx = layout.anchorLeft(canvasWidth) / Math.max(1f, canvasWidth);
        float y = area.bottom();
        List<CardSlot> slots = new ArrayList<>(stage.size());
        List<String> keys = new ArrayList<>(stage.size());
        for (Entry entry : stage) {
            float h = style.boxHeight() * scale;
            float w = Math.min(cardWidth(entry.view(), style, scale, settings, canvasWidth, canvasHeight, now),
                    area.w() - 6f);
            float x;
            if (layout.align() == LayoutSettings.Side.RIGHT) {
                x = Math.max(1f, Math.min(fx * area.w() - w, area.w() - 7f - w));
            } else {
                x = Math.max(1f, Math.min(fx * area.w(), area.w() - 7f - w));
            }
            keys.add(entry.view().key());
            slots.add(new CardSlot(entry.view(), area.x() + x, move.y(entry.view().key(), y - h, now), w, h));
            y -= h + gap;
        }
        move.retain(new HashSet<>(keys));
        if (!slots.isEmpty()) {
            painter.paint(gui, previewCanvas(style, scale, settings, canvasWidth, canvasHeight, now), slots);
        }
    }

    // ------------------------------------------------------------------
    // 量卡：与真卡同一套公式，只把"名字预算"换成面板给的
    // ------------------------------------------------------------------

    /** 预览面板的名字截断预算：卡壳被面板夹窄后，字与壳必须说同一种话（同一把尺）。 */
    private PickupCardSettings panelSettings(StyleModel style, float scale, float areaW,
                                             float canvasWidth, float canvasHeight, long now) {
        float room = Math.max(24f, (areaW - 6f) / scale);
        CardCanvas probe = previewCanvas(style, scale, PickupCardConfig.snapshot(),
                canvasWidth, canvasHeight, now);
        // 【多扣一个 gap】名字预算只扣到"数量开头"的话，名字顶满预算时卡的自然宽度会比
        // 面板可用宽多出 gap*scale，随后被 min(area.w()-6) 夹窄——右对齐的数量就压到名字
        // 尾巴上（英文长名样例里"…+1"贴死就是它）。多让出一个 gap，夹窄永远轮不到发生。
        int nameRoom = (int) Math.max(12f,
                room - CardMetrics.namelessWidth(probe, Minecraft.getInstance().font, sampleAmount())
                        - style.gap());
        return withNameLimit(PickupCardConfig.snapshot(), nameRoom);
    }

    /** 名字预算用的数量：舞台看队首，特写看静止卡，缩影看第一张示意卡。 */
    private int sampleAmount() {
        if (!stage.isEmpty()) {
            return stage.peekFirst().view().notice().count();
        }
        if (staticCard != null) {
            return staticCard.notice().count();
        }
        return Sample.COMMON.amount;
    }

    /** 一份快照的副本：名字宽度上限额外夹进面板给的预算（0 = 自动 → 直接用预算）。 */
    private static PickupCardSettings withNameLimit(PickupCardSettings s, int room) {
        int limit = s.nameMaxWidth() > 0 ? Math.min(s.nameMaxWidth(), room) : room;
        return new PickupCardSettings(s.holdMs(), s.exitMs(), s.mergeMode(), s.maxOnScreen(),
                s.queueSize(), s.countFormat(), s.enabled(), s.showItemName(), s.showItemId(),
                limit, s.fullPolicy(), s.countMode(), s.magnetEnabled(), s.magnetRadius());
    }

    /** 一张样例在当前缩放下的实际宽度 —— 走真卡的 {@code CardMetrics}，不另写一份公式。 */
    private float cardWidth(CardView view, StyleModel style, float scale, PickupCardSettings settings,
                            float canvasWidth, float canvasHeight, long now) {
        return CardMetrics.naturalWidth(previewCanvas(style, scale, settings, canvasWidth, canvasHeight, now),
                Minecraft.getInstance().font, view) * scale;
    }

    /** 这一帧要用的画布 —— 缩放与主题都按界面上当前生效的值给，预览才不会说谎。 */
    private CardCanvas previewCanvas(StyleModel style, float scale, PickupCardSettings settings,
                                     float canvasWidth, float canvasHeight, long now) {
        return new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                        style.bumpEnabled()),
                style, settings, PickupCardConfig.layoutSnapshot(),
                (int) canvasWidth, (int) canvasHeight, scale);
    }

    // ------------------------------------------------------------------

    /** 给 harness 的读数：模式、几张卡、各自画在哪个 y、key 是谁 —— 重叠与否只有数字能定案。 */
    public String stateDump() {
        if (mode == Mode.MINIMAP) {
            return "缩影";
        }
        if (mode != Mode.STAGE) {
            return "静止卡";
        }
        StringBuilder ys = new StringBuilder();
        StringBuilder keys = new StringBuilder();
        for (Entry entry : stage) {
            if (ys.length() > 0) {
                ys.append(", ");
                keys.append(", ");
            }
            keys.append(entry.view().key());
            Float drawn = move.drawnY(entry.view().key());
            ys.append(drawn == null ? "?" : String.format(Locale.ROOT, "%.1f", drawn));
        }
        return "舞台" + stage.size() + "张 y=[" + ys + "] 键=[" + keys + "]";
    }
}
