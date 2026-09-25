package com.niuqu.pickupcard.text;

/**
 * 卡上那个数字回答的是<b>哪个问题</b>。
 * <p>
 * {@link #PICKUP}（默认）回答「这次捡到了几个」—— 0.2.2 及以前的口径；
 * {@link #TOTAL} 回答「背包里现在有几个」—— 实时跟随物品栏，捡了会涨、用了会掉。
 * 两档只改数字的含义，写法（{@code +64 / ×64 / 1.2K}…）仍由 {@link CountFormat} 管。
 */
public enum CountMode {

    /** 本次拾取进账多少（默认口径）。 */
    PICKUP,

    /** 背包内持有总数：实时跟随物品栏，只算原版物品栏（主背包 + 盔甲 + 副手）。 */
    TOTAL
}
