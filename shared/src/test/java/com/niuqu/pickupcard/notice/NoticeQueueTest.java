package com.niuqu.pickupcard.notice;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账本规则：合并、排队、退场、救回。
 * <p>
 * 【排队的判据来自 0.1.0】它的 {@code enqueue} 是"屏上没满就上屏，满了就 addLast 排队，
 * 排队也满就丢"，{@code tick} 里 pollFirst 补位。这几条行为在这里逐条钉住 ——
 * 老版本是行为标杆，凭印象改会漂。
 */
class NoticeQueueTest {

    /** 屏上上限与排队上限：大多数用例用这两个数。 */
    private static final int CAP = 5;
    private static final int QUEUE = 9;

    /** 载荷用 String 代替 ItemStack：队列规则与物品类型无关，单测因此不必启动游戏。 */
    private NoticeQueue<String> queue() {
        return new NoticeQueue<>();
    }

    private static NoticeQueue.Outcome<String> add(NoticeQueue<String> q, String key, int n, long now) {
        return add(q, key, n, now, CAP, QUEUE);
    }

    private static NoticeQueue.Outcome<String> add(NoticeQueue<String> q, String key, int n,
                                                   long now, int cap, int queueSize) {
        return q.absorb(key, "l", "s", n, true, now, MergeMode.SAME_NBT, cap, queueSize);
    }

    @Test
    @DisplayName("同一物品合并：数量累加，代数 +1")
    void mergesSameItem() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "diamond-stack", 32, true, 1_000L, MergeMode.SAME_NBT, CAP, QUEUE);
        var outcome = q.absorb("diamond", "rare", "diamond-stack", 64, false, 1_500L,
                MergeMode.SAME_NBT, CAP, QUEUE);

        assertEquals(NoticeQueue.Change.MERGED, outcome.change());
        assertEquals(96, outcome.notice().count());
        assertEquals(1, outcome.notice().generation());
        assertEquals(1, q.size(), "合并之后屏上仍然只有一张卡");
        assertTrue(outcome.notice().firstTime());
    }

    /**
     * 用户 2026-09-17 报的那个 bug 的对照：隔很久（5 秒）再捡同一个物品，<b>只要那张卡还在
     * 屏上就必须合并</b>。
     */
    @Test
    @DisplayName("只要那张卡还在屏上，隔多久都合并（合并窗口已删）")
    void mergesAsLongAsTheCardIsAlive() {
        NoticeQueue<String> q = queue();
        add(q, "diamond", 1, 0L);
        add(q, "other", 1, 100L);

        var outcome = q.absorb("diamond", "l", "s", 1, false, 5_000L, MergeMode.SAME_NBT, CAP, QUEUE);

        assertEquals(NoticeQueue.Change.MERGED, outcome.change(), "卡还在就该并，不是顶掉重来");
        assertEquals(2, outcome.notice().count());
        assertTrue(outcome.evicted().isEmpty(), "不许因为这次拾取把别的卡挤掉");
        assertEquals(2, q.size());
    }

    @Test
    @DisplayName("NEVER 档：每次都单开一张（旧卡走淘汰通道，否则界面上会留两张同名卡）")
    void neverMergesMode() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "s", 1, true, 0L, MergeMode.NEVER, CAP, QUEUE);
        var outcome = q.absorb("diamond", "rare", "s", 1, false, 10L, MergeMode.NEVER, CAP, QUEUE);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
        assertEquals(1, outcome.evicted().size());
        assertEquals("diamond", outcome.evicted().get(0).key());
    }

    @Test
    @DisplayName("外观不同不许合并：同名物品换档会串档位")
    void differentLookDoesNotMerge() {
        NoticeQueue<String> q = queue();
        q.absorb("sword", "common", "s", 1, true, 0L, MergeMode.SAME_NBT, CAP, QUEUE);
        var outcome = q.absorb("sword", "epic", "s", 1, false, 100L, MergeMode.SAME_NBT, CAP, QUEUE);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
    }

    @Test
    @DisplayName("不同物品互不干扰，各自一张卡")
    void differentKeysAreIndependent() {
        NoticeQueue<String> q = queue();
        add(q, "diamond", 1, 0L);
        add(q, "iron", 1, 10L);
        assertEquals(2, q.size());
        assertEquals(1, q.find("diamond").orElseThrow().count());
    }

    // ------------------------------------------------------------------
    // 排队（0.1.0 的语义）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("屏上满了就排队：不顶掉别人，屏上保持原样")
    void queuesWhenTheScreenIsFull() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L, 2, QUEUE);
        add(q, "b", 1, 10L, 2, QUEUE);

        var third = add(q, "c", 1, 20L, 2, QUEUE);

        assertEquals(NoticeQueue.Change.QUEUED, third.change());
        assertEquals(2, q.size(), "屏上还是那两张");
        assertEquals(1, q.pendingSize(), "c 在队里等着");
        assertTrue(q.find("a").isPresent(), "最先上屏的那张不该被顶掉 —— 从前它是被挤走的那张");
        assertTrue(third.evicted().isEmpty(), "排队不该产生退场");
    }

    @Test
    @DisplayName("先来先上屏：按排队顺序补位，而且上屏时重新起算停留期")
    void promotesFirstInFirstOut() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L, 2, QUEUE);
        add(q, "b", 1, 10L, 2, QUEUE);
        add(q, "c", 1, 20L, 2, QUEUE);
        add(q, "d", 1, 30L, 2, QUEUE);
        assertEquals(2, q.pendingSize());

        // a 到点（100-0 >= 95），b 还差一点（100-10 < 95）→ 只空出一个位子
        assertEquals(1, q.sweep(100L, 95L).size());
        var promoted = q.promote(100L, 2);

        assertEquals(1, promoted.size());
        assertEquals("c", promoted.get(0).key(), "队首先上屏");
        assertEquals(100L, promoted.get(0).bornAt(), "上屏时重新起算停留期");
        assertEquals(1, q.pendingSize(), "d 还在队里");
        assertEquals(2, q.size());

        // b 也到点 → d 补上
        q.sweep(200L, 95L);
        var second = q.promote(200L, 2);
        assertEquals(1, second.size());
        assertEquals("d", second.get(0).key());
        assertEquals(0, q.pendingSize(), "队空了");
    }

    @Test
    @DisplayName("排队也满 → 丢掉这次拾取：屏上、队里都不动")
    void dropsWhenTheQueueIsFull() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L, 2, 1);
        add(q, "b", 1, 10L, 2, 1);
        add(q, "c", 1, 20L, 2, 1);                 // 排队 1/1
        var dropped = add(q, "d", 1, 30L, 2, 1);   // 队也满了

        assertEquals(NoticeQueue.Change.DROPPED, dropped.change());
        assertEquals(2, q.size());
        assertEquals(1, q.pendingSize());
        assertFalse(q.find("d").isPresent());
    }

    @Test
    @DisplayName("排队上限 0 = 不排队：屏满之后直接丢")
    void zeroQueueDropsImmediately() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L, 1, 0);
        var second = add(q, "b", 1, 10L, 1, 0);

        assertEquals(NoticeQueue.Change.DROPPED, second.change());
        assertEquals(1, q.size());
        assertEquals(0, q.pendingSize());
    }

    @Test
    @DisplayName("排队里的同名会并进排队那张，不会排出一串")
    void mergesIntoTheQueuedOne() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L, 1, QUEUE);              // 屏上 1/1
        add(q, "c", 1, 10L, 1, QUEUE);             // 排队
        var merged = add(q, "c", 4, 20L, 1, QUEUE);

        assertEquals(NoticeQueue.Change.MERGED, merged.change());
        assertEquals(1, q.pendingSize(), "还是队里那一张");
        assertEquals(5, merged.notice().count(), "数量累加到排队那张身上");

        // 等 a 退场，补位上来的应该是"并过 5 个"的那张
        q.sweep(1_000L, 50L);
        var promoted = q.promote(1_000L, 1);
        assertEquals(1, promoted.size());
        assertEquals("c", promoted.get(0).key());
        assertEquals(5, promoted.get(0).count());
    }

    // ------------------------------------------------------------------
    // 退场与救回
    // ------------------------------------------------------------------

    @Test
    @DisplayName("停留超时才退场，刚进来的不退")
    void sweepRespectsHold() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L);

        assertTrue(q.sweep(500L, 2_000L).isEmpty(), "还在停留期内");
        assertEquals(1, q.size());

        assertEquals(1, q.sweep(2_000L, 2_000L).size(), "到点退场");
        assertEquals(0, q.size());
    }

    @Test
    @DisplayName("合并会刷新停留计时：连捡不停，卡就不该消失")
    void mergeRefreshesLifetime() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L);
        add(q, "a", 1, 1_500L);

        assertTrue(q.sweep(2_500L, 2_000L).isEmpty(), "1.5s 时被刷新过，2.5s 时不该退场");
    }

    /**
     * 用户报的那个 bug 的核心对照：<b>退场中的那张被同一个物品救回</b>。
     * 要求：并回去（不是单开一张）、数量累加、不挤掉别人，渲染层收到 MERGED 后改播「淡回」。
     */
    @Test
    @DisplayName("退场中的卡被同一个物品救回：并回去，不重新排队、不挤掉下一张")
    void rescuesACardThatIsStillFading() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L);
        add(q, "b", 1, 10L);
        assertEquals(2, q.sweep(5_000L, 100L).size(), "两张都到点退场");
        assertTrue(q.isLeaving("a"), "退场中的那张账本还记着（屏幕还在淡出）");

        var rescued = q.absorb("a", "l", "s", 1, false, 5_100L, MergeMode.SAME_NBT, CAP, QUEUE);

        assertEquals(NoticeQueue.Change.MERGED, rescued.change(), "退场中的同名卡应该被救回");
        assertEquals(2, rescued.notice().count(), "数量要累加到它身上");
        assertTrue(rescued.evicted().isEmpty(), "救回不该顺手挤掉别的卡");
        assertFalse(q.isLeaving("a"), "救回之后它不再是「离开中」");
        assertTrue(q.find("a").isPresent(), "它回到活表里了");
    }

    @Test
    @DisplayName("退场播完（渲染层通知）之后，同名拾取就是新的一张卡")
    void afterTheFadeFinishesTheNameIsFreeAgain() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L);
        q.sweep(5_000L, 100L);
        q.forgetLeft("a");

        var outcome = add(q, "a", 1, 6_000L);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
        assertEquals(1, outcome.notice().count(), "新的一张卡只记这一次");
    }

    @Test
    @DisplayName("快照按最久没被碰过排前，渲染层反序就是最新在上")
    void snapshotOrder() {
        NoticeQueue<String> q = queue();
        add(q, "old", 1, 0L);
        add(q, "new", 1, 100L);

        var snap = q.snapshot();
        assertEquals("old", snap.get(0).key());
        assertEquals("new", snap.get(1).key());
    }

    // ------------------------------------------------------------------
    // 溢出卡（屏满 + 队满的兜底）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("溢出卡不占同屏名额：位子空出来照样补给排队的那个")
    void overflowCardDoesNotTakeASlot() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L, 1, 0);                        // 屏 1/1，且不排队
        var spill = q.absorbOverflow("~overflow", "~overflow", "spill", 1, 10L);

        assertEquals(NoticeQueue.Change.ADDED, spill.change());
        assertEquals(2, q.size(), "溢出卡在屏上");
        assertEquals(NoticeQueue.Change.QUEUED, add(q, "b", 1, 20L, 1, QUEUE).change(),
                "名额没被溢出卡占掉：b 仍然排得进队");

        // a（touched=0）到点，溢出卡（touched=10）还没到 —— 只空出一个位子
        assertEquals(1, q.sweep(60L, 55L).size(), "a 到点退场");
        var promoted = q.promote(60L, 1);
        assertEquals(1, promoted.size(), "空出来的名额要补给 b（溢出卡不算名额）");
        assertEquals("b", promoted.get(0).key());
    }

    @Test
    @DisplayName("溢出卡自己也会到点退场，退场之后名额还回去")
    void overflowCardExpiresLikeAnyOther() {
        NoticeQueue<String> q = queue();
        q.absorbOverflow("~overflow", "~overflow", "spill", 1, 0L);
        assertEquals(1, q.size());

        assertEquals(1, q.sweep(1_000L, 100L).size(), "它没有特权，到点一样退场");
        assertEquals(0, q.size(), "退场之后名额还回去");
        assertEquals(NoticeQueue.Change.ADDED, add(q, "a", 1, 1_100L, 1, 0).change(),
                "名额回来了：新拾取直接上屏");
    }

    @Test
    @DisplayName("继续溢出时并进同一张：数量累加、代数 +1")
    void overflowMerges() {
        NoticeQueue<String> q = queue();
        q.absorbOverflow("~overflow", "~overflow", "first", 1, 0L);
        var again = q.absorbOverflow("~overflow", "~overflow", "second", 1, 50L);

        assertEquals(NoticeQueue.Change.MERGED, again.change());
        assertEquals(2, again.notice().count(), "「还有 N 项」的 N 在累加");
        assertEquals(1, again.notice().generation());
        assertEquals(1, q.size());
        assertEquals("second", again.notice().payload(), "载荷换成调用方并好的新列表");
    }

    @Test
    @DisplayName("退回排队：从队头回、活表同步摘掉，位子空出后先回先上")
    void requeueFrontPutsCardsBackAtTheHeadAndOffTheAliveList() {
        NoticeQueue<String> q = queue();
        add(q, "a", 1, 0L);
        add(q, "b", 1, 1L);
        add(q, "c", 1, 2L);
        assertEquals(3, q.size());

        // 几何放不下：最老的两张（a、b）退回 —— 队头顺序保持 a 在 b 前（先回先上）
        var a = q.find("a").orElseThrow();
        var b = q.find("b").orElseThrow();
        q.requeueFront(List.of(a, b));

        assertEquals(1, q.size(), "活表只剩 c —— realSize 降下来，位子才算真的空出");
        assertEquals(2, q.pendingSize());

        // 退回的卡不再在活表里：同名新拾取按"排队那张"并进去（与老排队合并同一条路），
        // 而不是在屏幕上凭空表示同一件东西两张卡
        var fresh = add(q, "a", 1, 3L, 1, 9);
        assertEquals(NoticeQueue.Change.MERGED, fresh.change(),
                "a 的同名拾取并进排队里那张，数量累加");
        assertEquals(2, fresh.notice().count());

        // 位子空出来：先回先上 —— a 第一个回来（reborn：重新起算停留期）
        assertEquals(1, q.sweep(1_000L, 500L).size(), "c 到点退场");
        var promoted = q.promote(1_000L, 1);
        assertEquals(1, promoted.size());
        assertEquals("a", promoted.get(0).key(), "退回时排在队头的 a 先补位");
        assertEquals(1_000L, promoted.get(0).bornAt(), "补位那张从此刻重新出生（bornAt = 补位时刻）");
    }
}
