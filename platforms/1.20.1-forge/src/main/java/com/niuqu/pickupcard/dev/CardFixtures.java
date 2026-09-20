package com.niuqu.pickupcard.dev;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * harness 的固定样例卡集。
 * <p>
 * 【为什么必须固定】"这次改完比上次好看吗"只有在<b>每次都看同一组卡</b>的前提下才是个
 * 可回答的问题。上一版美术定不了稿，很大一部分原因是每次看到的卡都不一样——名字长度、
 * 数量位数、稀有度随手就变，讨论的是不同的东西。
 * <p>
 * 【为什么覆盖这几类】每一张都在钉一个边界：
 * 四档稀有度（强调色）、经验卡（独立色系、没有 ItemStack）、超长名字（会撞卡宽上限）、
 * 超大数量（会走数量缩写）、单字名字（会撞名字区下限）、中文名（字宽与英文不同）。
 * 改画法之后这六类都不出问题，才算没把已有的东西弄坏。
 */
public final class CardFixtures {

    /**
     * 一张样例卡。
     *
     * @param label  显示在 harness 上的名字
     * @param stack  物品栈；{@link ItemStack#EMPTY} 表示经验卡
     * @param amount 拾取数量
     */
    public record Fixture(String label, ItemStack stack, int amount) {

        public boolean xp() {
            return stack.isEmpty();
        }
    }

    private CardFixtures() {
    }

    /** 全部样例。每次调用现算——注册表在 dev 屏打开时才保证已经就绪。 */
    public static List<Fixture> all() {
        List<Fixture> list = new ArrayList<>();

        // 四档稀有度：不写死物品名，而是从注册表里按 rarity 找，跨版本不会因为
        // "某个物品被改了稀有度"而让这一组悄悄少一张。
        // 【必须排掉空气】注册表里第一个 COMMON 就是 minecraft:air，照单全收会得到
        // 一张空栈样例——它不报错、只是安静地展示成空名字，是最难发现的那种坏样例。
        Set<Item> used = new HashSet<>();
        // 【为什么命令方块必须第一】它是"实体渲染层物品"（entitySolid/entityCutout，
        // NO_BLEND）的代表 —— 这一类图标的淡出靠 RGB 向卡面靠拢而不是 alpha 混合
        // （见 NvgCardPainter 的淡出注释）。底锚下最老的卡在堆顶、也最先到点退场，
        // 退场连拍拍到的就是它：这类物品的淡出一回潮，连拍立刻能看见。
        list.add(new Fixture("command-block", new ItemStack(Items.COMMAND_BLOCK), 1));
        for (Rarity rarity : Rarity.values()) {
            Item item = itemOf(rarity, used);
            used.add(item);
            list.add(new Fixture(fixtureLabel(rarity, item), new ItemStack(item), 1));
        }

        list.add(new Fixture("xp", ItemStack.EMPTY, 137));
        // 【为什么真附魔】"附魔"不能只写在名字里：2026-09-19 起图标每帧原版现渲，
        // 退场连拍要验的就是 glint 条纹在淡出中仍在滚动（烘焙快照时代它被冻住）——
        // 没有真附魔组件就没有 glint，这条验收是空的。
        ItemStack enchantedSword =
                named(Items.DIAMOND_SWORD, "被铁砧改了名字的附魔钻石剑（超长名字边界测试）");
        enchantedSword.enchant(Enchantments.SHARPNESS, 1);
        list.add(new Fixture("long-name", enchantedSword, 1));
        // 【为什么用钻石而不是圆石】内置忽略表已经删掉了，现在圆石也会弹卡。
        // 留着钻石是因为它认得出、数量大、而且不会被任何默认规则牵动 ——
        // 样例集最怕的就是"悄悄变了"，所以宁可挑一件绝不会有歧义的物品。
        list.add(new Fixture("big-count", new ItemStack(Items.DIAMOND), 99_999));
        list.add(new Fixture("short-name", named(Items.STONE, "石"), 1));
        list.add(new Fixture("cjk", named(Items.NETHERITE_INGOT, "下界合金锭"), 64));

        return List.copyOf(list);
    }

    /**
     * 分页的样例集：每页不超过账本的 {@code maxOnScreen}，所以一页注入进去能全部留下。
     * <p>
     * 【为什么必须分页】账本上限 5 张、淘汰最老的。把 9 张一次性灌进去，被挤掉的是
     * <b>最早注入的那 4 张</b>——也就是四档稀有度。结果是"每张卡都注入过，但稀有度
     * 永远看不到"，而这件事不会报错、只在截图里表现为"怎么少了几张"。
     * <p>
     * 第 1 页看颜色（四档强调色 + 经验卡的独立色系），第 2 页看边界
     * （卡宽上限 / 数量缩写 / 单字下限 / 中文字宽）。
     */
    public static List<List<Fixture>> pages() {
        List<Fixture> all = all();
        return List.of(all.subList(0, 5), all.subList(5, all.size()));
    }

    /**
     * 像素对照用的固定样例：三段式卡（竖条 / 图标框 / 信息框），与真卡同一套几何。
     * <p>
     * 【为什么不直接复用上面那组】像素对照的前提是"两边画的是同一张卡"。
     * 上面那组的四档稀有度是**从注册表按 rarity 现找**的，哪天某个物品改了稀有度，
     * 这一组就悄悄换了一张卡 —— 而对照表还会照常给出数字，看起来一切正常。
     * 所以这一组的物品名写死。
     * <p>
     * 【为什么这里没有经验卡】经验卡入场后会铺一层强调色的微光（NEW 角标的替代），
     * 那是有颜色、带软边的，会盖住框与框之间的间隙，间隙就量不出来了。
     * 这一组只管几何，颜色另有页面看。
     */
    public static List<Fixture> measure() {
        return List.of(
                new Fixture("measure-common", new ItemStack(Items.STONE), 64),
                new Fixture("measure-uncommon", new ItemStack(Items.ELYTRA), 1),
                new Fixture("measure-rare", new ItemStack(Items.BEACON), 1),
                new Fixture("measure-epic", new ItemStack(Items.DRAGON_EGG), 1));
    }

    /** 把一张样例送进账本——走的是和真实拾取完全相同的那条路。 */
    public static void inject(Fixture fixture) {
        if (fixture.xp()) {
            Inbox.INSTANCE.offer(new CardContent.Experience(), fixture.amount());
        } else {
            Inbox.INSTANCE.offer(new CardContent.Item(fixture.stack().copy()), fixture.amount());
        }
    }

    public static void injectAll() {
        List<Fixture> fixtures = all();
        // 打出来是刻意的：fixture 一旦悄悄变了，"这次比上次好看吗"就不再是个能回答的问题，
        // 而变化的迹象只会在截图里，肉眼看不出来
        PickupCard.LOGGER.info("[harness] 样例集: {}", fixtures.stream()
                .map(f -> f.label() + "x" + f.amount())
                .collect(java.util.stream.Collectors.joining(", ")));
        for (Fixture fixture : fixtures) {
            inject(fixture);
        }
    }

    /** 清空在屏的卡与账本。换一组样例前先清，免得看不出来谁是谁。 */
    public static void clear() {
        Inbox.INSTANCE.reset();
        CardStage.INSTANCE.clear();
    }

    /** 标签带上解析结果（稀有度 + 物品 id），日志里一眼能看出这一组到底是哪几张卡。 */
    private static String fixtureLabel(Rarity rarity, Item item) {
        return rarity.name().toLowerCase(Locale.ROOT) + ":" + BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    /**
     * 取一个该稀有度、且还没被用过的物品。
     * <p>
     * 【为什么排空气】见 {@link #all()}。空栈不会让任何东西报错，只会让这一组样例
     * 悄悄少一张有意义的卡——而"样例悄悄变了"正是这套 fixture 要防的事。
     */
    private static Item itemOf(Rarity rarity, Set<Item> exclude) {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .filter(item -> !exclude.contains(item))
                .filter(item -> item.getRarity(new ItemStack(item)) == rarity)
                .findFirst()
                .orElse(Items.STONE);
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name));
        return stack;
    }
}
