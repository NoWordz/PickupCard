package com.niuqu.pickupcard.notice;

/**
 * 一张卡的数据。
 * <p>
 * 【为什么 payload 是泛型】队列的规则（合并窗口、上位淘汰、存活时长）与"物品是什么"
 * 毫无关系，而物品类型 {@code ItemStack} 在 Yarn 与官方名里是两个不同的类名。把载荷
 * 泛型化之后，这一整套规则留在 shared/ 里、能在单测里跑；物品类型只出现在映射层。
 * <p>
 * 【key 与 lookKey 分开】key 认得出"是不是同一个物品"（含 NBT），lookKey 认得出
 * "该不该长得一样"（稀有度档位、附魔）。合并要求两者都相同：只比物品会让不同稀有度的
 * 同名物并进一张卡，卡面档位与内容就对不上了。
 *
 * @param key        物品身份键（同 key 才可能合并）
 * @param lookKey    外观键（同 lookKey 才会共用一个卡面档位）
 * @param payload    平台载荷：1.20.1 上是 ItemStack 的副本，纯逻辑层不认识它
 * @param count      这张卡当前累计的数量
 * @param firstTime  是不是"这个人头一次遇到这件物品"（NEW 角标）
 * @param bornAt     这张卡出生时刻（入场动画的起点）
 * @param touchedAt  最近一次被合并刷新的时刻，合并窗口从它起算
 * @param generation 被合并过几次。渲染层用它判断"要不要重放一次入场动画" —— 时间戳
 *                   比较会踩时钟精度，计数器不会
 */
public record Notice<T>(String key,
                        String lookKey,
                        T payload,
                        int count,
                        boolean firstTime,
                        long bornAt,
                        long touchedAt,
                        int generation) {

    /** 合并：数量累加、刷新 touchedAt、代数 +1，其余原样。 */
    public Notice<T> mergeInto(int addAmount, long now) {
        return new Notice<>(key, lookKey, payload, count + addAmount, firstTime,
                bornAt, now, generation + 1);
    }

    /**
     * 排队的那张轮到上屏了：从这一刻重新出生。
     * <p>
     * 【为什么必须重新起算】排队里的 Notice 出生时间是"被捡到那一刻"，而 {@code expiredAt} 是拿
     * {@code touchedAt} 与停留时长比的 —— 不重算的话，排了几秒才轮到的卡可能刚上屏就到点。
     * 0.1.0 的补位也是这么做的（它补位时直接 new 一张）。
     */
    public Notice<T> reborn(long now) {
        return new Notice<>(key, lookKey, payload, count, firstTime, now, now, generation);
    }

    /** 这张卡在 {@code now} 这一刻该不该退场。 */
    public boolean expiredAt(long now, long holdMs) {
        if (holdMs <= 0L) return true;
        return now - touchedAt >= holdMs;
    }
}
