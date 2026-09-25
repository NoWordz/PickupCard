package com.niuqu.pickupcard.pickup;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.filter.FilterRules;
import com.niuqu.pickupcard.notice.MergeMode;
import com.niuqu.pickupcard.filter.FilterSettings;
import com.niuqu.pickupcard.filter.FilterSubject;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.NoticeQueue;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.notice.SeenItems;
import com.niuqu.pickupcard.render.InventoryTotals;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 拾取的中枢账本：过滤 → 记账（合并/淘汰/NEW）→ 把"发生了什么"排成事件等渲染层来取。
 * <p>
 * 【为什么账本不直接画】账本与渲染的生命周期不同：拾取事件在包处理时到达，渲染每帧
 * 才发生一次。中间放一条事件队列，两侧就各自独立——换渲染方案（自绘/九宫格/别的框架）
 * 不用动这里，反过来改合并规则也不用碰渲染。
 * <p>
 * 【线程】{@link #offer} 的上游是原版包处理（已在主线程），{@link #tick} 的上游是客户端
 * tick，两者同线程，事件队列因此不需要任何同步。
 */
public final class Inbox {

    /** 经验卡的身份键：世界里只有一种经验，永远合并进同一张卡。 */
    public static final String XP_KEY = "experience";

    /**
     * 溢出卡的身份键。物品键一定是 {@code namespace:path} 的形状，这一把不可能撞上；
     * 前缀是 {@code ~} 也是为了让日志里一眼看出"这不是一件东西"。
     */
    public static final String OVERFLOW_KEY = "~overflow";

    public static final Inbox INSTANCE = new Inbox();

    /** 一张卡的完整载荷：内容本体 + 过滤给的强调标记。 */
    public record Card(CardContent content, boolean emphasized) {
    }

    /** 一次拾取在账本上引起的变化。渲染层据此决定挂卡、改数字还是播退场。 */
    public sealed interface Event {
        record Added(Notice<Card> notice) implements Event {
        }

        /** 并进了已有卡：改数字/代数即可；同帧可能还有别的卡被连带顶掉（救回守恒顶卡），照 Evicted 各自处理。 */
        record Merged(Notice<Card> notice) implements Event {
        }

        /** 被新卡顶掉或超出在屏上限：账本已没有它，渲染播完退场就摘。 */
        record Evicted(Notice<Card> notice) implements Event {
        }

        /** 停留超时自然退场。 */
        record Expired(Notice<Card> notice) implements Event {
        }
    }

    private final NoticeQueue<Card> queue = new NoticeQueue<>();
    private final SeenItems seen = new SeenItems();
    private final List<Event> pending = new ArrayList<>();

    private Supplier<PickupCardSettings> settingsSource = PickupCardSettings::defaults;
    private Supplier<FilterSettings> filterSource = FilterSettings::defaults;

    /**
     * 渲染层这一帧量出来的<b>几何容量</b>（锚线以上真实放得下几张）。
     * <p>【为什么账本要问渲染要这个数】"同屏上限"是玩家的意愿，"画布上放得下几张"是屏幕的
     * 物理 —— 从前这两者打架时渲染层直接硬切摘卡，拾取无声蒸发（第四批反馈"锚定错乱"的根）。
     * 现在渲染层每帧把几何容量递过来，补位（promote）与新卡（absorb）都拿
     * {@code min(同屏上限, 几何容量)} 当闸门：放不下的根本不上屏，在队列里等，
     * 等到缩放/锚点/画布腾出位子。
     */
    private volatile int geometryCapacity = Integer.MAX_VALUE;

    /** 渲染层每帧回报几何容量；0 = 连一张都放不下。 */
    public void setGeometryCapacity(int capacity) {
        this.geometryCapacity = Math.max(0, capacity);
    }

    private Inbox() {
    }

    /**
     * 把一次"挤不进去"的拾取并进溢出卡的成员列表。
     * <p>
     * 【为什么这一层做合并】账本对内容一无所知（它是泛型的），成员的合并只能在认识
     * {@link CardContent} 的这一层做：读回当前那张溢出卡的成员、追加、把新列表交回去。
     * 只留 {@link CardContent.Overflow#MAX_ICONS} 个 —— 再多也看不出区别，白占内存。
     */
    private CardContent.Overflow overflowWith(CardContent dropped) {
        List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
        queue.find(OVERFLOW_KEY).ifPresent(notice -> {
            if (notice.payload().content() instanceof CardContent.Overflow old) {
                stacks.addAll(old.stacks());
            }
        });
        if (dropped instanceof CardContent.Item item
                && stacks.size() < CardContent.Overflow.MAX_ICONS) {
            stacks.add(item.stack());
        }
        return new CardContent.Overflow(List.copyOf(stacks));
    }

    /**
     * 被过滤器丢弃时的回调。默认什么都不做。
     * <p>
     * 【为什么必须有一个】被过滤掉的拾取在玩家那边表现为"什么都没发生"，
     * 和"mod 坏了"完全分不出来 —— 实测就有人捡了一路圆石来问"怎么一张卡都不弹"。
     * 丢弃是<b>故意</b>的行为，但不能是<b>无声</b>的。平台层接上日志。
     */
    private volatile Consumer<CardContent.Item> dropReporter = item -> {
    };

    /** 平台侧接日志。传 null 就恢复成什么都不做。 */
    public void setDropReporter(Consumer<CardContent.Item> reporter) {
        this.dropReporter = reporter == null ? item -> {
        } : reporter;
    }

    /** 平台侧把配置接进来。账本只认函数，不认 Forge——测试可以给固定值。 */
    public void setSources(Supplier<PickupCardSettings> settings, Supplier<FilterSettings> filter) {
        this.settingsSource = settings == null ? PickupCardSettings::defaults : settings;
        this.filterSource = filter == null ? FilterSettings::defaults : filter;
    }

    private PickupCardSettings settings() {
        return settingsSource.get().sanitized();
    }

    /** 渲染层要读的几项设置（退场时长、数量格式）。渲染只读，不归它改。 */
    public PickupCardSettings settingsSnapshot() {
        return settings();
    }

    /**
     * 收下一次拾取（主线程）。
     *
     * @return 这条拾取算出来的过滤判定，原样交出去 —— 调用方读
     *         {@link FilterRules.Decision#muted()} 决定要不要压掉原版拾取音（见
     *         {@link PickupSoundGate}）。
     *         <p>【为什么返回整个判定而不是一个布尔】判定就是在这里算出来的，原样交出，
     *         调用方就不会"漏掉某一支的结论"。这里曾经只回一个"该不该压音"的 bool，
     *         而静音名单那一支忘了带上它（只有黑名单那一支带了，偏偏黑名单恒为 false），
     *         于是"静音名单命中的话原版拾取音也压掉"这句话在文档里写了三处、实现里一处没有，
     *         静默缺失了很久也没人发现。
     */
    public FilterRules.Decision offer(CardContent content, int amount) {
        int count = Math.max(1, amount);
        long now = System.currentTimeMillis();

        String key;
        String look;
        boolean emphasized;
        // 经验卡与未知内容不走过滤表：先给一个"照常弹、不静音"的判定，让出口只剩一处
        FilterRules.Decision decision = FilterRules.Decision.PLAIN;
        if (content instanceof CardContent.Item item) {
            decision = FilterRules.check(subjectOf(item.stack()), filterSource.get());
            if (!decision.show()) {
                // 丢弃前留个声：玩家的观感是"没反应"，而日志里必须能看出是它干的
                dropReporter.accept(item);
                return decision;
            }
            MergeMode mode = settings().mergeMode();
            key = ItemIdentity.keyOf(item.stack(), mode);
            look = ItemIdentity.lookOf(item.stack(), mode);
            emphasized = decision.emphasized();
        } else if (content instanceof CardContent.Experience) {
            // 经验不过滤：它是正反馈本身，也没有"捡错一堆"的刷屏问题
            key = XP_KEY;
            look = XP_KEY;
            emphasized = false;
        } else {
            // 密封接口上不该有别的实现；真有就当作"什么都不弹、也不压音"
            return FilterRules.Decision.DROP;
        }

        // 【第一次见的判据固定用最细的键】NONE / 改名件每一张的身份键都不同，
        // 拿它去问「第一次见」会天天报 NEW —— 那个角标问的是物品，不是这一次拾取。
        String seenKey = content instanceof CardContent.Item item
                ? ItemIdentity.strictKeyOf(item.stack()) : key;
        boolean firstTime = seen.markAndCheckFirst(seenKey);
        Card card = new Card(content, emphasized);
        // 【闸门 = min(意愿, 物理)】几何放不下的不上屏，走同一条排队/溢出路径
        int capacity = Math.min(settings().maxOnScreen(), geometryCapacity);
        NoticeQueue.Outcome<Card> outcome = queue.absorb(key, look, card, count, firstTime, now,
                settings().mergeMode(), capacity, settings().queueSize(), settings().fullPolicy());

        for (Notice<Card> evicted : outcome.evicted()) {
            pending.add(new Event.Evicted(evicted));
        }
        switch (outcome.change()) {
            case ADDED -> pending.add(new Event.Added(outcome.notice()));
            case MERGED -> pending.add(new Event.Merged(outcome.notice()));
            case EVICTED -> {
                // absorb 的顶替淘汰走上面那个列表；这个分支只在协议变动时才会出现
                pending.add(new Event.Evicted(outcome.notice()));
            }
            // 排队与丢弃在屏幕上都"什么都不发生"：排队的会在补位时变成 Added，
            // 丢弃的（屏满 + 队满）只能靠日志说明白 —— 玩家看到的是"这次没弹"。
            case QUEUED -> PickupCard.LOGGER.info("[排队] key={} 屏上已经 {} 张，等位子",
                    outcome.notice().key(), capacity);
            case DROPPED -> {
                // 屏满 + 队满：并进"还有 N 项"那张卡，而不是静默丢掉
                CardContent.Overflow overflow = overflowWith(content);
                NoticeQueue.Outcome<Card> spilled = queue.absorbOverflow(OVERFLOW_KEY, OVERFLOW_KEY,
                        new Card(overflow, false), 1, now);
                pending.add(spilled.change() == NoticeQueue.Change.ADDED
                        ? new Event.Added(spilled.notice())
                        : new Event.Merged(spilled.notice()));
                PickupCard.LOGGER.info("[溢出] key={}：屏满且队满（同屏 {} / 排队 {}），并进溢出卡（第 {} 项）",
                        outcome.notice().key(), capacity, settings().queueSize(),
                        spilled.notice().count());
            }
        }
        return decision;
    }

    /**
     * 渲染层把一张卡的退场播完了：账本这边也把「离开中」的记忘掉（此后再捡到同一个物品
     * 就是新的一张卡，而不是"救回一张已经不在屏幕上的卡"）。
     */
    public void forgetLeft(String key) {
        queue.forgetLeft(key);
    }

    /**
     * 渲染层量出来"几何上放不下"的那几张：退回排队<b>队头</b>，位子一空第一个回来。
     * <p>【为什么取代了硬切】从前这里直接摘卡 + forgetLeft —— 拾取在屏幕上无声蒸发，
     * 连排队都不进（第四批反馈"锚定错乱"的根因之一）。退回排队后行为与"屏满排队"
     * 完全同一条路：先来先上屏，回来时重播入场、重新起算停留期。
     */
    public void requeue(List<Notice<Card>> notices) {
        if (notices.isEmpty()) {
            return;
        }
        queue.requeueFront(notices);
        for (Notice<Card> notice : notices) {
            PickupCard.LOGGER.info("[退回排队] key={}：几何上放不下，等位子（先回先上）", notice.key());
        }
    }

    /**
     * 客户端 tick：取走积累的事件，把到点的卡变成 {@link Event.Expired}。
     * 渲染层对 Expired 的卡播退场动画；账本这边它们已经不存在了。
     */
    public List<Event> tick() {
        long now = System.currentTimeMillis();
        List<Event> events = new ArrayList<>(pending);
        pending.clear();

        for (Notice<Card> expired : queue.sweep(now, settings().holdMs())) {
            events.add(new Event.Expired(expired));
        }
        // 空出位子就补位（先来先上屏）。补上的那张算"新的卡"：入场动画照播。
        for (Notice<Card> promoted : queue.promote(now, settings().maxOnScreen())) {
            events.add(new Event.Added(promoted));
        }
        return events;
    }

    /** 换世界/退出/总开关关掉：队列、NEW 账本、未取走的事件、在途的静音判定一起回到"什么都没发生"。 */
    public void reset() {
        queue.clear();
        seen.clear();
        pending.clear();
        // 静音闸门也算"这一次拾取"的在途状态：换世界时清零，免得残留跨越世界边界
        PickupSoundGate.arm(false);
        // 持有总数表同理：不清的话新世界的第 1 tick 会撞上旧表的 tick 号，
        // 总数口径的卡会画出上一个世界背包里剩的东西
        InventoryTotals.reset();
    }

    private static FilterSubject subjectOf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Set<String> tags = new HashSet<>();
        // 1.20.1 上 Holder#tags() 返回 Stream：每次拾取现查一次，拾取是稀疏事件，不值得缓存
        stack.getItem().builtInRegistryHolder().tags()
                .forEach(tag -> tags.add(tag.location().toString()));
        return new FilterSubject(id.toString(), id.getNamespace(), tags);
    }
}
