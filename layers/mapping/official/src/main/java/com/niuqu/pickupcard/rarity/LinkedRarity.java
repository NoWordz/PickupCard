package com.niuqu.pickupcard.rarity;

import net.minecraft.world.item.ItemStack;

/**
 * 稀有度联动的交接面：装了 RarityCore 这类「稀有度来源」mod 时，平台层在启动时注入实现；
 * 没装就没人注入，一切回落 vanilla 四档（见 {@link RarityAccent}）。
 *
 * <p>【为什么是个接口而不是直接调 RarityCore】两层隔离：一是编译期——shared/layers 不该为
 * 一个可选 mod 长出编译依赖（CI 机器上没有那个 jar）；二是卸载——桥出了问题或要下架时，
 * 不注入就是完整解除，渲染路径一行不用改。档位统一成 1~7 的整数（vanilla 四档按
 * common=1…epic=4 落进同一把尺），特效阶梯（扫光、微光）直接拿这把尺用。
 */
public interface LinkedRarity {

    /**
     * 这个物品的联动档位。<b>0 = 没有联动档位</b>（没装联动 mod、桥解析失败、或这个物品
     * 在联动那边没有档位）——调用方据此回落 vanilla 映射，不算错误。
     */
    int tierOf(ItemStack stack);

    /** 联动档位的强调色（ARGB，不透明）。只会在 {@code tierOf} 给过 ≥1 的档位上被问。 */
    int colorOf(int tier);

    /**
     * 「高稀有」的下限档（含）：入场扫光这类奖励性特效从这一档起跑。
     * <p>【为什么由联动方说了算】档位越多的来源，同一档位的含金量越低——vanilla 四档里
     * rare(3) 就值得庆祝；RarityCore 七档里 Legendary(5) 才是同一个心理位置。统一
     * "tier ≥ 3" 的话，RC 玩家的稀有卡会廉价地满屏扫光。
     */
    int showcaseFrom();
}
