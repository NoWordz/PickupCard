package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.pickup.Inbox;

/**
 * 账本里的一张卡在渲染侧的影子：账本的 {@link Notice} 是权威数据，这里只多记
 * <b>渲染才关心</b>的两个时刻。
 * <p>
 * 【为什么要分开记两处时刻】{@code bornAt}（入场起点）与 {@code touchedAt}（合并窗口）
 * 是账本的语义；{@code lastBumpAt}（数字跳动的起点）与 {@code exitStartAt}（退场起点）
 * 纯粹是"屏幕上的这一次表演什么时候开始的"。合并会刷新跳动、也会把正在退场的卡拽回来，
 * 但这些都不该反过来改账本。
 */
public final class CardView {

    /** 退场还没开始。用负数而不是 0 是因为 0 是合法的纪元时刻（测试里会用到）。 */
    public static final long NO_EXIT = -1L;

    private Notice<Inbox.Card> notice;
    private long lastBumpAt = -1L;
    /**
     * 上一次合并之前的数量（没有合并过时 = 当前数量）。
     * <p>【为什么要留着旧值】数字滚动要"从旧值滚到新值"，而账本只给得出新值 ——
     * 旧值不在这里记下来就永远拿不到了。它纯属"屏幕上的这一次表演"，所以归 CardView。
     */
    private int prevCount;
    private long exitStartAt = NO_EXIT;
    /** 淡回的起点（被救回时记）；{@code NO_EXIT} = 没在淡回。 */
    private long reviveAt = NO_EXIT;
    /**
     * 文本度量备忘（名字 / 数量 / 截断结果与宽度）。
     * <p>【为什么挂在卡上】同一个物品名每一帧要被问两次（布局量宽、绘制），而每次问都要
     * 新建 Component、重跑翻译模板、逐码点量宽 —— 见 {@link CardTextCache} 的类注释。
     * 挂在这里的好处是生命周期自动对齐：卡被摘掉时缓存跟着一起没，不需要另开一张 map 剪枝。
     */
    private final CardTextCache text = new CardTextCache();

    public CardView(Notice<Inbox.Card> notice) {
        this.notice = notice;
        this.prevCount = notice.count();
    }

    /** 这张卡的文本度量备忘（懒刷新，键没变就是空操作）。 */
    public CardTextCache text() {
        return text;
    }

    public Notice<Inbox.Card> notice() {
        return notice;
    }

    public String key() {
        return notice.key();
    }

    public long lastBumpAt() {
        return lastBumpAt;
    }

    /** 上一次合并之前的数量（没合并过就是当前数量）。 */
    public int prevCount() {
        return prevCount;
    }

    public long exitStartAt() {
        return exitStartAt;
    }

    /** 正在退场。淡回中的卡不算（它是活的：既不该被摘掉，也不该被再判一次退场）。 */
    public boolean exiting() {
        return exitStartAt != NO_EXIT && reviveAt == NO_EXIT;
    }

    /** 正在淡回：退场被撤销，不透明度从撤消那一刻往回补到 1。 */
    public boolean reviving() {
        return reviveAt != NO_EXIT;
    }

    /** 淡回是从哪一刻开始的（画 alpha 用）。 */
    public long reviveAt() {
        return reviveAt;
    }

    /**
     * 被救回（同一个物品又捡到了）：不瞬间回到全不透明，而是从「退场已经播到哪儿」补回去，
     * 时长是主题里的 {@code --pc-revive-ms}（见 {@code StyleModel#reviveMs()}）。
     */
    public void beginRevive(long now) {
        if (exitStartAt != NO_EXIT && reviveAt == NO_EXIT) {
            reviveAt = now;
        }
    }

    /** 淡回播完：这一张重新算「活着」（exitStartAt 一起清，否则下次退场会从半路开始）。 */
    public void endRevive() {
        if (reviveAt != NO_EXIT) {
            exitStartAt = NO_EXIT;
            reviveAt = NO_EXIT;
        }
    }

    /** 合并：换掉账本快照，顺便让数字跳一下。正在退场的那张改播「淡回」。 */
    public void absorbMerge(Notice<Inbox.Card> merged, long now) {
        this.prevCount = this.notice.count();
        this.notice = merged;
        this.lastBumpAt = now;
        if (exitStartAt != NO_EXIT) {
            beginRevive(now);
        }
    }

    /** 开始退场。已经在退场中的不重来（否则每 tick 都会被推后）。 */
    public void beginExit(long now) {
        if (exitStartAt == NO_EXIT) {
            exitStartAt = now;
        }
    }
}
