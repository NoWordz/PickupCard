package com.niuqu.pickupcard.rarity;

import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.world.item.ItemStack;

/**
 * 稀有度 → 强调色。<b>具体取哪一套色由主题给</b>（{@link StyleModel.Accents}）。
 *
 * <p>【为什么这里不再写死色值】它原来把 {@code #9AA4AD / #FFD83D / #55EBFF / #D78BFF /
 * #7DFF8A} 硬编码了一遍，而主题 JSON 里 {@code accent.*} 从第一天就写着同样六个数 ——
 * 一份真源两份数据：玩家（或资源包）改主题时，Java 里这份不会跟着动，卡片的强调色就
 * "改了没反应"。现在这里只负责<b>把 vanilla 的四档映射到主题给的四个位置</b>，
 * 是"哪一档"的知识，不是"什么颜色"的知识。
 *
 * <p>【RarityCore 联动接在哪】它的七档 + 玩家自定义取色将来覆盖的就是一个
 * {@code Accents} 实例 —— 交接面从六个常量变成一个值对象，bridge 出问题或要下架时，
 * 回到 {@code style.accents()} 就完全解除了。{@code tokens.css} 里那句注释
 * "装了 RarityCore 时由它接管，这里的值作为无联动时的兜底"说的正是这件事。
 */
public final class RarityAccent {

    private RarityAccent() {
    }

    /**
     * 平台层启动时注入的联动（RarityCore 桥）；null = 没装联动 mod。
     * <p>【为什么是 setter 注入】与 {@code CardStage#setSources} 同一款交接：shared/layers
     * 不碰 Forge 的 ModList，装没装由平台层判断，这里只认「有人交实现」这件事。
     */
    private static volatile LinkedRarity linked;

    /** 注入联动实现；传 null 即解除（桥出问题时平台层自己兜底，一般用不到）。 */
    public static void setLinked(LinkedRarity linkedRarity) {
        linked = linkedRarity;
    }

    /**
     * 物品的强调色：<b>联动说了算</b>（RarityCore 档位的玩家自定义色）；没装联动、或这个
     * 物品在联动那边没有档位，才按 vanilla 稀有度挑主题里的那一档。
     * <p>【为什么不用 switch 写联动】七档颜色是联动 mod 的数据，不是这把尺的知识——
     * 这把尺只负责"先问联动、再问主题"这个顺序。
     */
    public static int of(ItemStack stack, StyleModel.Accents a) {
        LinkedRarity link = linked;
        if (link != null) {
            int tier = link.tierOf(stack);
            if (tier >= 1) {
                return link.colorOf(tier);
            }
        }
        return switch (stack.getRarity()) {
            case UNCOMMON -> a.uncommon();
            case RARE -> a.rare();
            case EPIC -> a.epic();
            default -> a.common();
        };
    }

    /**
     * 物品的稀有度档位（特效阶梯的统一尺）：联动档位 1~7 优先；没联动回落 vanilla 四档
     * （common=1…epic=4，与联动的前四档同序同义）。
     */
    public static int tierOf(ItemStack stack) {
        LinkedRarity link = linked;
        if (link != null) {
            int tier = link.tierOf(stack);
            if (tier >= 1) {
                return tier;
            }
        }
        return switch (stack.getRarity()) {
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            default -> 1;
        };
    }

    /**
     * 「高稀有」的下限档（含）：扫光这类一次性奖励特效从这一档起跑。
     * 有联动时由联动方给（RarityCore 是 legendary 5，见桥），没联动是 vanilla 的 rare(3)。
     */
    public static int showcaseFrom() {
        LinkedRarity link = linked;
        return link != null ? link.showcaseFrom() : 3;
    }

    /** 经验卡的强调色：走主题里单独的一档，<b>刻意不参与稀有度分级</b>。 */
    public static int xp(StyleModel.Accents a) {
        return a.xp();
    }

    /**
     * 溢出卡（"还有 N 项"）的强调色。
     * <p>【为什么不跟着成员走】它的成员混着各种稀有度，跟着谁都会让"这张卡是什么档"说不清；
     * 它本来就不是一件东西，而是一个"还有更多"的信号。
     */
    public static int overflow(StyleModel.Accents a) {
        return a.overflow();
    }
}
