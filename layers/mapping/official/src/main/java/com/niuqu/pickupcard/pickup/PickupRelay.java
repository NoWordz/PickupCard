package com.niuqu.pickupcard.pickup;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.filter.FilterRules;
import com.niuqu.pickupcard.magnet.MagnetMath;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 把"某个实体被捡起了"翻译成"我捡到了什么"，交给账本。
 * <p>
 * 【谁捡的必须查——这是 v0.1.0 的真 bug】这个包广播视野内<b>所有人</b>的拾取，
 * {@code getPlayerId()} 是拾取者的实体 id。v0.1.0 从来没查过它：其他玩家捡了东西
 * 会给你弹卡，僵尸捡起装备也会给你弹卡。现在非玩家拾取与他人的拾取都在源头丢弃，
 * 只留"我"的。物品实体与经验球都走这个包（经验球经 {@code LivingEntity.take} 广播，
 * 字节码核对过），所以 XP 卡是同一个入口。
 * <p>
 * 【世界里实体还在才认识它】改名过的、带附魔/NBT 的物品只有实体身上那份才是全的。
 * 实体已经被移除（网络竞态）就直接放弃——旧版曾退回"按数字 id 造一个栈"，但那个
 * 数字是<b>网络实体 id</b>，当注册表 id 用造出来的要么是空气要么是错物品，宁可不弹。
 * <p>
 * 【线程】Mixin 注入在 {@code ensureRunningOnSameThread} 之后，已经在主线程，
 * 可以直接操作账本。改注入点时记得一起看这里——前提一旦破坏，症状是偶发崩溃。
 */
public final class PickupRelay {

    /**
     * 记账慢过这条线就报一条日志（微秒）。
     * <p>取 4000：60fps 的一帧是 16667us，这条路径单独吃掉 4ms 就已经能在帧率上看见；
     * 而它平时是几十微秒（一次 item 注册表查询 + 一个栈复制）。定了这条线，
     * 「捡起来好卡」下次会自己带着"哪一次、什么东西、多久"回来。
     */
    private static final long SLOW_PICKUP_MICROS = 4_000L;

    private PickupRelay() {
    }

    public static void onTakeItem(@Nullable ClientLevel level, ClientboundTakeItemEntityPacket packet) {
        if (level == null) return;
        if (!(level.getEntity(packet.getPlayerId()) instanceof AbstractClientPlayer collector)) return;
        if (!isSelf(collector)) return;

        // 【拾取这一下花了多久】这条路径跑在包处理里（渲染线程），任何一次变慢都会直接
        // 变成玩家看到的掉帧 —— 而且拖慢它的东西大多不在这个类里（过滤规则现查 tag、
        // 稀有度联动的首次解析、第一张卡的引擎初始化）。超线就报一条日志，把"哪一次拾取、
        // 多久"钉住 —— 没有它，用户报的「捡起来好卡」只能靠猜（2026-09-20 就是这么开始的）。
        long t0 = System.nanoTime();
        try {
            Entity carried = level.getEntity(packet.getItemId());
            if (carried instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) return;
                // 必须复制：实体下一 tick 就可能被移除，而我们的卡要活好几秒
                FilterRules.Decision decision =
                        Inbox.INSTANCE.offer(new CardContent.Item(stack.copy()), packet.getAmount());
                // 【时序】先写闸门，原版随后放音时会来读它。两者在同一次方法调用、同一线程里，
                // 见 PickupSoundGate 的类注释 —— 注入点一旦挪到放音之后就失效。
                PickupSoundGate.arm(decision.muted());
            } else if (carried instanceof ExperienceOrb) {
                // 数量就是包里的经验值（take 广播的是实际吸收的量）
                Inbox.INSTANCE.offer(new CardContent.Experience(), packet.getAmount());
                // 【两个分支都必须 arm】经验不过滤，判定恒不静音 —— 但"写下不压"这一步不能省：
                // 闸门是静态的，万一上一条拾取留了 true 没被读走（原版没走到放音），漏掉这一步
                // 就会把经验球那一声吃掉。写下 false 是兜底，也是"出口唯一"的实证。
                PickupSoundGate.arm(false);
            }
        } finally {
            long micros = (System.nanoTime() - t0) / 1_000L;
            if (micros >= SLOW_PICKUP_MICROS) {
                PickupCard.LOGGER.warn("[拾取] 这次记账花了 {}us（阈值 {}）—— 会直接算进掉帧；"
                                + "看它前后的日志（首张卡引擎初始化、联动解析、过滤规则现查）",
                        micros, SLOW_PICKUP_MICROS);
            }
        }
    }

    /**
     * 磁铁/漏斗把某个物品实体的数量改小了（实体数据同步信号），before 是同步前的完整副本。
     * <p>
     * 【过滤链，从严到松】数量没变少（{@link MagnetMath#absorbed} = 0，含首同步 0->N）→
     * 磁铁开关 → 距离：先便宜后贵，绝大多数同步包（玩家、怪、盔甲架的元数据）在第一格
     * 就出局。距离用的是平方比较，省一次开方。
     * <p>
     * 【总开关为什么不在这里查】与 {@link #onTakeItem} 同一条分工：{@code enabled} 归渲染层
     * （{@code CardStage.renderInto}）管 —— 关掉时屏上清卡、账本重置，账本这边照常记账，
     * 两层各管各的。磁铁开关是本功能的独立闸门，才归这里。
     * <p>
     * 【为什么不 arm 静音闸门】{@code offer} 返回的判定这里原样丢弃：磁铁这条路不经过
     * {@code handleTakeItemEntity} 的放音点，闸门里写什么都不会被读 —— 写了反而留一份
     * 会被下一次拾取误读的状态。原版那声拾取音本来也不会响（没有拾取发生）。
     * <p>
     * 【过滤在 offer 里做】黑名单/白名单/静音名单照常生效：磁铁吸进来的也是"我得到的
     * 东西"，玩家对它配的规则应该跟亲手捡的一样。
     */
    public static void onMagnetSync(@Nullable ClientLevel level, int entityId,
                                    ItemStack before, int afterCount) {
        if (level == null) return;
        int amount = MagnetMath.absorbed(before.getCount(), afterCount);
        if (amount <= 0) return;
        PickupCardSettings settings = Inbox.INSTANCE.settingsSnapshot();
        if (!settings.magnetEnabled()) return;
        LocalPlayer self = Minecraft.getInstance().player;
        Entity entity = level.getEntity(entityId);
        if (self == null || entity == null) return;
        if (self.distanceToSqr(entity) > settings.magnetRadius() * settings.magnetRadius()) return;
        // 【before 不用再 copy】mixin 捕获时已经复制过一份（实体身上的栈随时会被服务端
        // 改写），relay 只是所有权的中转站 —— 再 copy 一次是白付的钱
        long t0 = System.nanoTime();
        Inbox.INSTANCE.offer(new CardContent.Item(before), amount);
        long micros = (System.nanoTime() - t0) / 1_000L;
        // 【一次性可观测（真机定位靠它）】弹卡本身不该刷屏，但"这条同步被当成了吸收"
        // 必须在日志里留痕 —— 真机排障（漏斗/磁铁/别的 mod 乱改数量）全指望这条。
        PickupCard.LOGGER.info("[磁铁] ItemEntity 数量 {} → {}，弹卡 {} 个（{}）",
                before.getCount(), afterCount, amount, before.getItem());
        if (micros >= SLOW_PICKUP_MICROS) {
            PickupCard.LOGGER.warn("[磁铁] 这次记账花了 {}us（阈值 {}）—— 会直接算进掉帧",
                    micros, SLOW_PICKUP_MICROS);
        }
    }

    /** UUID 比对而不是引用比对：本地玩家实体在换维度时可能被重建。 */
    private static boolean isSelf(AbstractClientPlayer collector) {
        LocalPlayer self = Minecraft.getInstance().player;
        return self != null && self.getUUID().equals(collector.getUUID());
    }
}
