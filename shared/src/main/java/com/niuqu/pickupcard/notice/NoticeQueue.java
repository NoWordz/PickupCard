package com.niuqu.pickupcard.notice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 屏幕上那几张卡的账本：谁在、谁被合并、谁被挤掉。纯逻辑，不碰渲染、不碰时钟。
 * <p>
 * 【为什么自己拿一套账本，而不是把"弹卡"交给通用提示队列】通用提示队列是"一条消息一次弹"，
 * 而拾取要的是【合并】—— 两秒内连捡 64 个钻石应该变成一张卡的数字在滚，不是弹 64 张。
 * 合并、数量累加、上位淘汰这套规则是拾取特有的，得自己拿着。
 *
 * @param <T> 平台载荷类型（1.20.1 上是 ItemStack 副本）
 */
public final class NoticeQueue<T> {

    /** 一条卡的生命周期里发生了什么，交给调用方去同步界面。 */
    public enum Change {
        /** 新开一张卡：需要新建一张卡并播入场。 */
        ADDED,
        /** 并进了已有卡：只需要把数量与代数写回那张卡。 */
        MERGED,
        /** 已有卡被挤掉（超出同时在屏数量）：需要播退场后移除。 */
        EVICTED,
        /** 屏上满了：这次拾取排到队尾，等有卡退场再上屏（此刻屏幕上什么都不会发生）。 */
        QUEUED,
        /** 屏上满了、队也排满了：这次拾取被丢掉（屏幕上什么都不会发生）。 */
        DROPPED
    }

    /**
     * 一次 {@link #absorb} 的结果。
     * <p>
     * 【为什么带着 evicted】队列内部会静默地把超限的卡挤掉，可渲染层得让被挤掉的
     * 那张播退场再移除。如果这里只回"新卡是谁"，被挤掉的那张就会永远留在屏幕上 ——
     * 账本和界面各说各话。淘汰结果必须跟着返回值一起出去。
     */
    public record Outcome<T>(Change change, Notice<T> notice, List<Notice<T>> evicted) {
        public Outcome {
            evicted = List.copyOf(evicted);
        }
    }

    private final Map<String, Notice<T>> alive = new LinkedHashMap<>();

    /**
     * 已经离开活表、但屏幕上还在播退场的卡。
     * <p>
     * 【为什么账本要记这个】退场是渲染层的事（{@code exitMs} 几百毫秒），账本这边是瞬间的：
     * 淘汰/到期那一刻它就不认识了，而屏幕上那张还在淡。这几百毫秒里再捡到同一个物品，
     * 账本只能单开一张新卡 —— 渲染层按物品为键，于是把淡出那张<b>整张换掉</b>：画面就是
     * 「淡到一半突然全不透明，顺手把别的卡挤出局」（用户 2026-09-17 报的 bug）。
     * 留一份它，这次拾取就能并回去、把它救回来，而且不用重新排队。
     */
    private final Map<String, Notice<T>> leaving = new LinkedHashMap<>();

    /** 等上屏的那些拾取，<b>先来先上屏</b>（FIFO）。出处：0.1.0 的 NoticeQueue#enqueue 用的就是 addLast/pollFirst。 */
    private final Deque<Notice<T>> pending = new ArrayDeque<>();

    /** 溢出卡的身份键（{@link #absorbOverflow} 设的）；没有溢出卡时是 null。 */
    private String overflowKey;

    /**
     * 收下一次拾取（屏满按旧行为排队 —— {@link FullPolicy#QUEUE} 档）。
     *
     * @param mergeMode    合并粒度（哪些拾取算同一件东西）；{@link MergeMode#NEVER} = 从不合并
     * @param maxOnScreen  同时在屏上限；满了就排队，不再顶掉别人
     * @param queueSize    排队上限；0 = 不排队（超出的直接丢）
     * @return 发生的改动；被淘汰的那张卡会作为结果返回（调用方据此让它播退场）
     */
    public Outcome<T> absorb(String key, String lookKey, T payload, int amount,
                             boolean firstTime, long now,
                             MergeMode mergeMode, int maxOnScreen, int queueSize) {
        return absorb(key, lookKey, payload, amount, firstTime, now,
                mergeMode, maxOnScreen, queueSize, FullPolicy.QUEUE);
    }

    /**
     * 收下一次拾取，并指定屏满之后的策略。
     *
     * @param mergeMode    合并粒度（哪些拾取算同一件东西）；{@link MergeMode#NEVER} = 从不合并
     * @param maxOnScreen  同时在屏上限
     * @param queueSize    排队上限；0 = 不排队（超出的直接丢）
     * @param fullPolicy   屏满了怎么办：{@link FullPolicy#REPLACE} 顶掉最老的正常卡、新卡立刻上屏；
     *                     {@link FullPolicy#QUEUE} 排到队尾等位（旧行为）
     * @return 发生的改动；被淘汰的那张卡会作为结果返回（调用方据此让它播退场）
     */
    public Outcome<T> absorb(String key, String lookKey, T payload, int amount,
                             boolean firstTime, long now,
                             MergeMode mergeMode, int maxOnScreen, int queueSize,
                             FullPolicy fullPolicy) {
        List<Notice<T>> evicted = new ArrayList<>();
        Notice<T> existing = alive.get(key);
        if (existing == null) {
            Notice<T> returning = leaving.get(key);
            if (returning != null
                    && MergeWindow.shouldMerge(true, returning.lookKey().equals(lookKey), mergeMode)) {
                leaving.remove(key);
                Notice<T> merged = returning.mergeInto(amount, now);
                // 【名额守恒】救回不查上限的老前提是"名额是 sweep 腾出来的"——REPLACE 顶卡不是：
                // 顶那一刻名额就被新卡占走，这时把它免费拉回来，realSize 就永久停在 max+1。
                // 超限就同帧把最老的一张正常卡顶出去：救回与顶卡一起完成，realSize 恒不超 max
                //（QUEUE 档下 alive 只会经 sweep 减员后再救回，天然守恒，这个分支不触发）。
                if (realSize() >= maxOnScreen) {
                    Notice<T> victim = oldestNormalCard();
                    if (victim != null) {
                        alive.remove(victim.key());
                        leaving.put(victim.key(), victim);   // 与 sweep 同一条离场路
                        evicted.add(victim);
                    }
                }
                alive.put(key, merged);
                return new Outcome<>(Change.MERGED, merged, evicted);
            }
        }
        if (existing != null) {
            boolean sameLook = existing.lookKey().equals(lookKey);
            // 【判据是「那张卡还在不在」，不是「隔了多久」】从前还有一条合并窗口（默认 1200ms）：
            // 超窗口就顶掉旧卡、单开一张 —— 可那时旧卡往往正在淡出，屏幕于是要表示同一物品
            // 两张卡（渲染层按物品为键，只能整张换掉 = 用户报的那个 bug）。
            if (MergeWindow.shouldMerge(true, sameLook, mergeMode)) {
                Notice<T> merged = existing.mergeInto(amount, now);
                alive.put(key, merged);
                return new Outcome<>(Change.MERGED, merged, evicted);
            }
            // 不能并：把旧卡顶掉，让位给新卡。视觉上就是"同一件东西又来了"，
            // 用新的出生时间重播入场比原地改数字更像是"又捡到了"。
            alive.remove(key);
            evicted.add(existing);
            leaving.put(key, existing);
        }
        // 排队里已经有同一个物品：并进排队那张，而不是再排一个 ——
        // 不然"连捡 10 个钻石"会在队里排出 10 个各自为政的拾取
        for (Notice<T> queued : pending) {
            if (queued.key().equals(key)
                    && MergeWindow.shouldMerge(true, queued.lookKey().equals(lookKey), mergeMode)) {
                Notice<T> merged = queued.mergeInto(amount, now);
                replaceQueued(queued, merged);
                return new Outcome<>(Change.MERGED, merged, evicted);
            }
        }

        Notice<T> fresh = new Notice<>(key, lookKey, payload, amount, firstTime, now, now, 0);
        if (realSize() >= maxOnScreen) {
            if (fullPolicy == FullPolicy.REPLACE) {
                // 【顶谁】touchedAt 最老的那张"正常卡"——溢出卡不占名额，也不配被顶
                //（realSize() 把它排除在外，这里必须一致，否则会把名额挤成负数）。
                // 选不出来（几何容量 0、alive 里只剩溢出卡）就不上账，落去排队：与 QUEUE 档同结局。
                Notice<T> victim = oldestNormalCard();
                if (victim != null) {
                    alive.remove(victim.key());
                    leaving.put(victim.key(), victim);   // 与 sweep 同一条离场路：退场期间同名拾取能救回
                    evicted.add(victim);
                    alive.put(key, fresh);
                    return new Outcome<>(Change.ADDED, fresh, evicted);
                }
            }
            // QUEUE 档：旧行为一字不动 —— 屏上满了：排到队尾。排队也满 → 丢掉这次拾取（0.1.0 的语义就是这样）。
            if (queueSize > 0 && pending.size() < queueSize) {
                pending.addLast(fresh);
                return new Outcome<>(Change.QUEUED, fresh, evicted);
            }
            return new Outcome<>(Change.DROPPED, fresh, evicted);
        }
        alive.put(key, fresh);
        return new Outcome<>(Change.ADDED, fresh, evicted);
    }

    /**
     * 在屏的<b>正常</b>卡数量：溢出卡不算名额。
     * <p>
     * 【为什么溢出卡不占名额】它本来就是"名额满了"那一刻诞生的 —— 让它占名额，要么立刻又超限，
     * 要么得把某张正常的卡挤掉，那就回到"捡一个丢一个"的老毛病。它只在被丢过东西之后存在，
     * 自己也会到点退场（{@link #sweep} 一视同仁）。
     */
    private int realSize() {
        return alive.size() - (overflowKey != null && alive.containsKey(overflowKey) ? 1 : 0);
    }

    /**
     * alive 里 touchedAt 最老的一张「正常卡」（溢出卡不占名额，也不配被顶）；一张都没有就是 null。
     * <p>【谁在用】REPLACE 顶卡与救回超限的连带顶卡 —— 两个"要挤掉一个人"的场合必须选同一个受害者，
     * 各写一遍迟早漂成两套口径。
     */
    private Notice<T> oldestNormalCard() {
        Notice<T> victim = null;
        for (Notice<T> candidate : alive.values()) {
            if (candidate.key().equals(overflowKey)) continue;
            if (victim == null || candidate.touchedAt() < victim.touchedAt()) {
                victim = candidate;
            }
        }
        return victim;
    }

    /**
     * 溢出：把一次"挤不进去"的拾取并进那张「还有 N 项」的卡（没有就新开一张）。
     * <p>
     * 【为什么载荷由调用方给】并成员列表是"内容类型"的事，账本对载荷一无所知（它是泛型的）。
     * 这里只管身份键、数量与位置。
     *
     * @param key     溢出卡的身份键（渲染层按它建档，必须稳定）
     * @param payload 调用方并好成员之后的新载荷
     * @return 并进去之后的改动（第一次是 ADDED，之后是 MERGED）
     */
    public Outcome<T> absorbOverflow(String key, String lookKey, T payload, int amount, long now) {
        overflowKey = key;
        Notice<T> existing = alive.get(key);
        Notice<T> merged = existing == null
                ? new Notice<>(key, lookKey, payload, amount, false, now, now, 0)
                : new Notice<>(key, lookKey, payload, existing.count() + amount, existing.firstTime(),
                        existing.bornAt(), now, existing.generation() + 1);
        alive.put(key, merged);
        return new Outcome<>(existing == null ? Change.ADDED : Change.MERGED, merged,
                new ArrayList<>());
    }

    /** 队列里换掉一张（并了数量之后）。ArrayDeque 不能按位置改，只能重建。 */
    private void replaceQueued(Notice<T> old, Notice<T> merged) {
        List<Notice<T>> all = new ArrayList<>(pending);
        pending.clear();
        for (Notice<T> notice : all) {
            pending.addLast(notice == old ? merged : notice);
        }
    }

    /**
     * 腾出位子就补位（先来先上屏）。上屏的那张**从此刻重新起算**停留期。
     *
     * @return 轮到上屏的那些卡，按上屏先后排列；调用方给它们发 Added 事件
     */
    public List<Notice<T>> promote(long now, int maxOnScreen) {
        List<Notice<T>> promoted = new ArrayList<>();
        while (!pending.isEmpty() && realSize() < maxOnScreen) {
            Notice<T> next = pending.pollFirst();
            if (next == null) break;
            Notice<T> reborn = next.reborn(now);
            alive.put(reborn.key(), reborn);
            promoted.add(reborn);
        }
        return promoted;
    }

    /**
     * 把几张已经在屏上的卡退回排队<b>队头</b>（几何上放不下了；2026-09-19 起，
     * 渲染层不再硬切摘卡 —— 那会让拾取无声蒸发）。从队头回，先来先上屏的次序不乱：
     * 最老的回到最前面，位子一空它第一个回来。回队的卡补位时会 {@code reborn}
     * （重新起算停留期、重播入场）—— "它回来这件事"本来就值得一帧入场。
     * <p>【活表必须同步摘掉】退回的卡不再算"在屏"：realSize 要降下来（位子才算真的空出），
     * 同名拾取也该按"排队那张"并进去，而不是往一张已经不在屏上的卡上滚数字。
     */
    public void requeueFront(List<Notice<T>> notices) {
        for (int i = notices.size() - 1; i >= 0; i--) {
            Notice<T> notice = notices.get(i);
            if (alive.remove(notice.key(), notice) && notice.key().equals(overflowKey)) {
                overflowKey = null;         // 溢出卡也能被退回；走它名额照旧不占
            }
            pending.addFirst(notice);
        }
    }

    /** 到点该退场的卡（停留超时），从账本里摘掉并返回。 */
    public List<Notice<T>> sweep(long now, long holdMs) {
        List<Notice<T>> gone = new ArrayList<>();
        alive.entrySet().removeIf(entry -> {
            if (entry.getValue().expiredAt(now, holdMs)) {
                gone.add(entry.getValue());
                return true;
            }
            return false;
        });
        for (Notice<T> notice : gone) {
            leaving.put(notice.key(), notice);
            if (notice.key().equals(overflowKey)) {
                overflowKey = null;         // 溢出卡自己也会到点退场，退场之后名额还回去
            }
        }
        return gone;
    }

    /** 这一张是不是「离开中」（账本已经不带它了，但屏幕上还在淡出）。 */
    public boolean isLeaving(String key) {
        return leaving.containsKey(key);
    }

    /** 渲染层把那几百毫秒播完了：忘掉它 —— 之后同名拾取就是新的一张卡了。 */
    public void forgetLeft(String key) {
        leaving.remove(key);
    }

    public Optional<Notice<T>> find(String key) {
        return Optional.ofNullable(alive.get(key));
    }

    /** 当前在屏的卡，按"最久没被碰过"排前 —— 渲染层倒序铺开就是"最新的在最上面"。 */
    public List<Notice<T>> snapshot() {
        List<Notice<T>> all = new ArrayList<>(alive.values());
        all.sort((a, b) -> Long.compare(a.touchedAt(), b.touchedAt()));
        return Collections.unmodifiableList(all);
    }

    public int size() {
        return alive.size();
    }

    /** 排队里有几张（诊断与单测用）。 */
    public int pendingSize() {
        return pending.size();
    }

    public void clear() {
        alive.clear();
        leaving.clear();
        pending.clear();
        overflowKey = null;
    }
}
