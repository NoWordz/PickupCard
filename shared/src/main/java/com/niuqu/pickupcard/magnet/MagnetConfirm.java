package com.niuqu.pickupcard.magnet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * 磁铁确认状态机：吸收信号（掉落物数量被服务端改小）押注 3 tick，等"自己背包真的涨了"
 * 才放行弹卡。
 *
 * @param <T> 对账键（物品）—— 增量确认按它查背包总量
 * @param <V> 弹卡负载（押注时的原 ItemStack）—— 同一物品可能带不同 NBT，弹卡要用押注那一份
 * <p>
 * 【为什么存在】信号本身不带"进了谁的背包"——别人的磁铁、别人的容器在玩家身边吸东西，
 * 客户端看到的是一模一样的数量变小（用户真机反馈的误弹源）。但<b>进我自己背包</b>这件事
 * 客户端看得到：背包里该物品的总量会涨。所以把"信号→弹卡"改成"信号→押注→背包增量确认
 * →弹卡"，误弹源就根治了；代价是弹卡最多晚 3 tick（150ms），无感。
 * <p>
 * 【基线为什么取押注时的"上一 tick"值】主时序是背包同步与信号<b>同 tick 到达</b>（服务端
 * 同一 tick 里先改背包再改掉落物、两包一起发）——押注那一刻当前总量已经含本次吸收，拿它
 * 当基线就永远等不到增量（误杀）。上一 tick 的值肯定不含本次，"当前 − 上一 tick 基线"
 * 在押注的第一天就能对上账。
 * <p>
 * 【窗口与丢弃】押注 tick + 3 内没涨够就整条丢弃（宁漏勿误：半张卡比不弹更糟）。被跳过的
 * 押注在下一次 {@link #confirm} 时一并超时。
 * <p>
 * 【与 MC 的关系】本类只做时间数学，背包总量由调用方喂（{@code current}/{@code previous}
 * 两个取数函数）—— layers 侧接 {@code InventoryTotals}。离线全测（MagnetConfirmTest）。
 */
public final class MagnetConfirm<T, V> {

    /** 押注从信号 tick 起最多等几 tick。3 tick = 150ms：无感上限。 */
    static final int WINDOW_TICKS = 3;

    /** 一条被确认的吸收：可以弹卡了（payload 是押注时的原件，不是对账键）。 */
    public record Confirmed<T, V>(T item, V payload, int amount) {}

    private static final class Entry<T, V> {
        final T item;
        final V payload;
        final int amount;
        final long pendingTick;
        final int baseline;

        Entry(T item, V payload, int amount, long pendingTick, int baseline) {
            this.item = item;
            this.payload = payload;
            this.amount = amount;
            this.pendingTick = pendingTick;
            this.baseline = baseline;
        }
    }

    private final ToIntFunction<T> current;
    private final ToIntFunction<T> previous;
    /** 最近一次"自己的背包被服务端改写"的 tick（背包 NBT 容器场景的唯一确认源），-1 = 从未。 */
    private final LongSupplier containerWrittenTick;
    private final List<Entry<T, V>> pending = new ArrayList<>();

    public MagnetConfirm(ToIntFunction<T> current, ToIntFunction<T> previous,
                         LongSupplier containerWrittenTick) {
        this.current = current;
        this.previous = previous;
        this.containerWrittenTick = containerWrittenTick;
    }

    /** 信号到达（tick = 玩家 tickCount）：先押注（带上弹卡要的原件），不当场弹卡。 */
    public void pending(T item, V payload, int amount, long tick) {
        if (amount <= 0) return;
        pending.add(new Entry<>(item, payload, amount, tick, previous.applyAsInt(item)));
    }

    /** 清空全部押注（换世界/总开关：旧世界的基线对不了新世界的账，整批作废）。 */
    public void clear() {
        pending.clear();
    }

    /**
     * 每 tick 调一次：对账 + 清超时。返回本 tick 确认的吸收（调用方负责弹卡）。
     * 遍历顺序保持押注顺序；同 tick 多次调用由调用方 tick 门控保证只跑一次。
     */
    public List<Confirmed<T, V>> confirm(long tick) {
        List<Confirmed<T, V>> out = new ArrayList<>();
        Iterator<Entry<T, V>> it = pending.iterator();
        while (it.hasNext()) {
            Entry<T, V> e = it.next();
            // 【超时判断在确认之前】过了窗口的条目彻底死掉 —— 哪怕之后自己背包涨了
            // （亲手捡的、别的来源），也不能把一条押注"复活"成弹卡，否则别人的磁铁
            // 每吸一次，我随手捡一次同物品就误弹一次。
            if (tick - e.pendingTick > WINDOW_TICKS) {
                it.remove();
            } else if (containerWrittenTick.getAsLong() >= e.pendingTick
                    || current.applyAsInt(e.item) - e.baseline >= e.amount) {
                // 【两条确认路，满足其一】① 押注后（含同 tick）我的背包被服务端改写过 ——
                // 背包 NBT 容器（SB 这类）场景的唯一证据：物品进了背包内部，41 格数量不变；
                // ② 数量增量达标 —— 物品直接进原版背包的强确认。别人的磁铁两条都不满足。
                out.add(new Confirmed<>(e.item, e.payload, e.amount));
                it.remove();
            }
        }
        return out;
    }
}
