package com.niuqu.pickupcard.pickup;

import com.niuqu.pickupcard.compat.MinecraftItemData;
import com.niuqu.pickupcard.notice.MergeMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * 从 ItemStack 抽出两把键：一把认人，一把认脸。
 * <p>
 * 【为什么要两把】{@code key} 答"是不是同一个东西"，{@code lookKey} 答"该不该长得一样"。
 * 粒度由 {@link MergeMode} 决定：SAME_NBT 档两把都要对得上，放宽的档位只看身份键 ——
 * 只比物品会让"普通附魔书"和"经验修补附魔书"并进同一张卡，
 * 而它们的稀有度档位不同，卡面就会跟内容对不上。
 * <p>
 * 【key 为什么不含数量】数量是被合并改写的那个值，把它编进键里，第二次拾取的键就变了，
 * 永远合并不起来 —— 这正是"为什么我捡了一组钻石却弹了两张卡"的经典死法。
 */
public final class ItemIdentity {

    /** 序号：给「不该合并的那一次拾取」发新名字用。{@code Inbox#offer} 在客户端主线程上跑。 */
    private static long sequence;

    private ItemIdentity() {
    }

    /**
     * 最细的身份键：物品 id + 完整物品数据。两个用处 —— SAME_NBT 档的键，以及
     * <b>「第一次见这个物品」的判据</b>（放宽的档位下它固定用这把键，否则刷屏的 NEW
     * 角标会天天报到：那角标问的是物品，不是这一次拾取）。
     * <p>
     * 改名、附魔、自定义数据都会体现在物品数据里，所以"我的钻石剑"和"你的钻石剑"
     * 是两件不同的东西 —— 这是想要的语义。
     */
    public static String strictKeyOf(ItemStack stack) {
        if (stack.isEmpty()) return "minecraft:air";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return MinecraftItemData.strictKey(stack, id);
    }

    /**
     * 外观键：稀有度 + 是否附魔 + 是否有损耗。这三样决定卡面档位与特效，
     * 任意一项不同就不该共用一张卡。
     */
    /** 这一档下「是不是同一个东西」的键。 */
    public static String keyOf(ItemStack stack, MergeMode mode) {
        if (stack.isEmpty()) return "minecraft:air";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (mode.uniquePerPickup(MinecraftItemData.hasCustomName(stack))) {
            // 每次拾取都要一张自己的卡：渲染层按账本的键建档，键相同就只能有一张
            return id + "#@" + (++sequence);
        }
        return mode.usesNbt() ? strictKeyOf(stack) : id;
    }

    /**
     * 外观键：稀有度 + 是否附魔 + 是否有损耗。这三样决定卡面档位与特效，
     * 任意一项不同就不该共用一张卡。
     * <p>
     * 【为什么只有 SAME_NBT 档给真的外观键】放宽的档位是玩家明确要求「同名就并」的 ——
     * 再用外观键把它挡回来，那一档就等于没设。空串表示「外观不参与判定」。
     */
    public static String lookOf(ItemStack stack, MergeMode mode) {
        if (!mode.usesLook()) return "";
        Rarity rarity = stack.getRarity();
        return rarity.name() + '|' + stack.isEnchanted() + '|' + stack.isDamaged();
    }
}
