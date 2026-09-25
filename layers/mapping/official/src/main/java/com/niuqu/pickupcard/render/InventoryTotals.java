package com.niuqu.pickupcard.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 当前 tick 的物品栏持有总数表（原版 41 格：主背包 + 盔甲 + 副手）。
 * <p>【为什么按 tick 缓存】卡片一帧要问两次数量（布局量宽、绘制各一次），而 41 格扫描
 * 虽便宜也不必每帧做两遍；tick 粒度（50ms）对"实时跟随"绰绰有余。
 * <p>【为什么只扫原版三组】Curios 等扩展槽不在 {@link Inventory} 的标准列表里，
 * 扫到它们要么引额外依赖、要么各 mod 各写一套 —— 配置说明里已写明"只算原版 41 格"。
 * <p>【为什么有 {@link #reset()}】tick 计数（{@code tickCount}）从 0 起：换世界后新玩家的
 * 第 1 tick 会撞上旧玩家的第 1 tick，不清表就会画出上一个世界的持有量。
 * 由 {@code Inbox.reset}（换世界/退出/总开关）一并调用 —— 那里本来就是"回到什么都没发生"的出口。
 */
public final class InventoryTotals {

    private static int lastTick = -1;
    private static final Map<Item, Integer> totals = new HashMap<>();

    private InventoryTotals() {
    }

    /** 这个物品现在背包里有几个（0 = 没有）。 */
    public static int of(Item item) {
        scanIfStale();
        return totals.getOrDefault(item, 0);
    }

    private static void scanIfStale() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            reset();
            return;
        }
        if (player.tickCount == lastTick) {
            return;
        }
        lastTick = player.tickCount;
        totals.clear();
        Inventory inv = player.getInventory();
        for (List<ItemStack> part : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack stack : part) {
                if (!stack.isEmpty()) {
                    totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }
    }

    /** 换世界/总开关关掉：清表并让下一次询问强制重扫。 */
    public static void reset() {
        lastTick = -1;
        totals.clear();
    }
}
