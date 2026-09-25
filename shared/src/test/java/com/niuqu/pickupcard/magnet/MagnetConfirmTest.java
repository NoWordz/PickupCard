package com.niuqu.pickupcard.magnet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 磁铁确认状态机 {@link MagnetConfirm} 的账：吸收信号必须等"自己背包真的涨了"才弹卡。
 * <p>
 * 【为什么值得钉】信号（掉落物数量变小）不带"进了谁的背包"——别人的磁铁在身边吸东西
 * 也会发同样的信号（用户真机反馈的误弹源）。确认制的判据只有一条：<b>押注期间自己背包
 * 里该物品的增量 ≥ 吸收量</b>；押注 3 tick 没等到就丢弃（宁漏勿误）。基线取<b>押注时
 * 上一 tick</b> 的总量 —— 主时序是背包同步与信号同 tick 到达（服务端同一 tick 里先改背包
 * 再改掉落物），当前值减上一 tick 基线立刻达标，不会因为"同步先到"被误杀。纯时间数学、
 * 不碰 MC，离线全测。
 */
class MagnetConfirmTest {

    /** 手动喂总量的替身：advance 推进一 tick，模拟"背包同步先到/后到/从不来"。 */
    private static final class FakeTotals {
        final Map<String, Integer> now = new HashMap<>();
        final Map<String, Integer> prev = new HashMap<>();

        int current(String item) { return now.getOrDefault(item, 0); }
        int previous(String item) { return prev.getOrDefault(item, 0); }

        void advance(String item, int newTotal) {
            prev.put(item, now.getOrDefault(item, 0));
            now.put(item, newTotal);
        }
    }

    @Test
    @DisplayName("主时序（背包与信号同 tick 到）：当前总量 − 上一 tick 基线 = 吸收量，立刻确认")
    void sameTickArrivalConfirmsImmediately() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);           // tick 99：背包 10
        totals.advance("stone", 14);           // tick 100：信号与背包同步同到（14，含吸收 4）
        MagnetConfirm<String, String> m = new MagnetConfirm<>(totals::current, totals::previous, () -> -1);
        m.pending("stone", "stone-stack", 4, 100);
        var out = m.confirm(100).confirmed();
        assertEquals(1, out.size());
        assertEquals(4, out.get(0).amount());
    }

    @Test
    @DisplayName("背包晚 1 tick 到：押注期内涨够，确认")
    void backpackArrivesOneTickLate() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);           // tick 99
        totals.advance("stone", 10);           // tick 100：信号到，背包还没同步
        MagnetConfirm<String, String> m = new MagnetConfirm<>(totals::current, totals::previous, () -> -1);
        m.pending("stone", "stone-stack", 4, 100);
        assertTrue(m.confirm(100).confirmed().isEmpty());
        totals.advance("stone", 14);           // tick 101：背包到了
        var out = m.confirm(101).confirmed();
        assertEquals(1, out.size());
        assertEquals(4, out.get(0).amount());
    }

    @Test
    @DisplayName("别人的磁铁：自己背包从不涨，押注窗口内不弹、超时丢弃")
    void someoneElsesMagnetNeverConfirms() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);
        totals.advance("stone", 10);           // tick 100：信号到，我的背包纹丝不动
        MagnetConfirm<String, String> m = new MagnetConfirm<>(totals::current, totals::previous, () -> -1);
        m.pending("stone", "stone-stack", 4, 100);
        assertTrue(m.confirm(100).confirmed().isEmpty());
        assertTrue(m.confirm(101).confirmed().isEmpty());
        assertTrue(m.confirm(102).confirmed().isEmpty());
        assertTrue(m.confirm(103).confirmed().isEmpty());  // 窗口最后一天：还是没涨
        assertTrue(m.confirm(104).confirmed().isEmpty());  // 超时已丢弃
        totals.advance("stone", 99);           // 之后背包怎么涨都与这条无关
        assertTrue(m.confirm(105).confirmed().isEmpty());
    }

    @Test
    @DisplayName("押注期内涨了但不够数：不弹半张卡，超时整条丢弃")
    void partialArrivalDoesNotPopHalf() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);
        totals.advance("stone", 12);           // 信号 tick：只涨 2 < 4
        MagnetConfirm<String, String> m = new MagnetConfirm<>(totals::current, totals::previous, () -> -1);
        m.pending("stone", "stone-stack", 4, 100);
        assertTrue(m.confirm(100).confirmed().isEmpty());
        assertTrue(m.confirm(103).confirmed().isEmpty());  // 到超时也没涨够
        totals.advance("stone", 40);           // 再涨也不复活
        assertTrue(m.confirm(104).confirmed().isEmpty());
    }

    @Test
    @DisplayName("同物品两笔押注：各自独立确认，互不顶替")
    void twoPendingEntriesConfirmIndependently() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);
        totals.advance("stone", 10);           // tick 100
        totals.advance("iron", 0);
        MagnetConfirm<String, String> m = new MagnetConfirm<>(totals::current, totals::previous, () -> -1);
        m.pending("stone", "stone-stack", 4, 100);
        m.pending("iron", "iron-stack", 8, 100);
        totals.advance("iron", 8);             // tick 101：iron 到了、stone 没到
        var out = m.confirm(101).confirmed();
        assertEquals(1, out.size());
        assertEquals("iron", out.get(0).item());
        assertEquals("iron-stack", out.get(0).payload());
        assertEquals(8, out.get(0).amount());
    }

    @Test
    @DisplayName("一口气跳过整个窗口：直接超时丢弃，之后背包涨了也不复活")
    void droppedEntriesStayDropped() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);
        totals.advance("stone", 10);
        MagnetConfirm<String, String> m = new MagnetConfirm<>(totals::current, totals::previous, () -> -1);
        m.pending("stone", "stone-stack", 4, 100);
        assertTrue(m.confirm(200).confirmed().isEmpty());  // 一次跳到 100 tick 后
        totals.advance("stone", 99);
        assertTrue(m.confirm(201).confirmed().isEmpty());
    }

    @Test
    @DisplayName("背包 NBT 容器场景（SB）：数量不变，但押注后我的背包槽被服务端改写 → 确认")
    void containerWrittenConfirmsEvenWithoutCountGrowth() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);
        totals.advance("stone", 10);           // 信号 tick：背包数量纹丝不动（进了背包 NBT）
        long[] written = {-1};
        MagnetConfirm<String, String> m = new MagnetConfirm<>(
                totals::current, totals::previous, () -> written[0]);
        m.pending("stone", "stone-stack", 1, 100);
        assertTrue(m.confirm(100).confirmed().isEmpty());  // 还没被写
        written[0] = 101;                      // 服务端同步背包槽（SetSlot containerId=0）
        var out = m.confirm(101).confirmed();
        assertEquals(1, out.size());
        assertEquals("stone-stack", out.get(0).payload());
    }

    @Test
    @DisplayName("别人的磁铁：数量不涨、我的背包也没被写 → 超时丢弃")
    void foreignAbsorptionHasNoConfirmationPath() {
        FakeTotals totals = new FakeTotals();
        totals.advance("stone", 10);
        totals.advance("stone", 10);
        MagnetConfirm<String, String> m = new MagnetConfirm<>(
                totals::current, totals::previous, () -> -1);
        m.pending("stone", "stone-stack", 4, 100);
        assertTrue(m.confirm(103).confirmed().isEmpty());
    }
}
