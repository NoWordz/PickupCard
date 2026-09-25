package com.niuqu.pickupcard.mixin;

import com.niuqu.pickupcard.pickup.PickupRelay;
import com.niuqu.pickupcard.pickup.PickupSoundGate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拾取的唯一真相来源：原版"某实体被捡起"包。
 * <p>
 * 【为什么是嗅探而不是自己的网络包】这样服务端什么都不用装：任何原版服务器、任何别的
 * mod 服务器，只要它用的是原版拾取流程，客户端就看得见。代价是拿不到"谁捡的"这类只有
 * 服务端知道的信息 —— 那些我们本来也不需要。
 * <p>
 * 【注入点为什么在 ensureRunningOnSameThread 之后】那一行之后才是主线程（能安全碰游戏状态），
 * 而且此时 {@code level} 里的物品实体还没被移除 —— 我们能顺着 {@code packet.getItemId()}
 * 把真正的 ItemStack 捞出来，改名过的、带 NBT 的都是原样。晚一步就只剩一个物品 id，
 * 显示出来的名字就不对了。
 * <p>
 * 【静音名单为什么要 redirect 放音这一处调用】原版这个方法在客户端自己
 * {@code level.playLocalSound(...)}（这一处 {@code handleTakeItemEntity} 里两次：经验球与物品），
 * 而整段方法 <b>不能</b> 用 {@code @Inject(cancellable = true)} 取消 —— 取消了连
 * 物品数量扣减与实体移除一起没了，屏幕上就留下一堆捡不掉的幽灵物品。所以只掐"放音"
 * 这一次调用：{@link PickupSoundGate} 里放着本次拾取的判定，压音时直接返回、不放。
 * <p>
 * 【这个类为什么不带 @OnlyIn】它住在映射层，要同时编给 Forge 与 NeoForge —— 而
 * {@code @OnlyIn} 的包名在两边不同（{@code net.minecraftforge...} / {@code net.neoforged...}）。
 * 客户端专属由 mixin 配置只在客户端加载来保证，不靠这个注解。
 * <p>
 * 【磁铁检测的注入同住这个类】{@code handleSetEntityData} 上的一对 @Inject（见下方
 * 磁铁段注释）盯的是"实体数据同步把物品数量改小"这个信号，与拾取包那条路互不干扰；
 * 判定与弹卡都在 {@link PickupRelay#onMagnetSync}。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    // =====================================================================
    // 【磁铁检测（Phase D）】别的 mod（磁铁升级、漏斗等）把地上物品吸进容器时，
    // 服务端走的是 ItemEntity.setItem(remaining) —— 客户端收到一次实体数据同步
    // （DATA_ITEM 槽），数量被改小。这里捕获同步前后的数量，交给 {@link PickupRelay}
    // 判断该不该弹卡。正常拾取走另一个包（上面的注入），despawn/岩浆/爆炸是直接
    // discard 实体、不发数据 —— 信号区分度就是这么来的。
    // 【签名与注入点为何长这样（2026-09-25 javap 47.4.10 核实，D1 探针定案）】
    // handleSetEntityData 开头 ensureRunningOnSameThread（字节码指令 6），随后
    // level.getEntity(packet.id())（指令 17）——所以 before 必须挂在同款 INVOKE+shift=AFTER 上
    // （HEAD 是网络线程，碰不得 level）；after 在 TAIL（原版已把 packedItems 应用进实体，
    // 此时实体现值就是同步后的数量）。
    // 【首同步不是吸取】实体刚生成时客户端 getItem() 为 EMPTY，第一包同步看到的是
    // 0 -> N —— MagnetMath.absorbed(0, x)=0 在 relay 侧正好挡住，这里不用特判。
    // =====================================================================

    /** 同步前的 ItemStack 副本（卡要带 NBT/名字活几秒，不能只存 count）。 */
    private ItemStack pickupcard$magnetBefore;
    private int pickupcard$magnetEntityId = -1;

    @Inject(method = "handleSetEntityData",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER))
    private void pickupcard$magnetCaptureBefore(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (this.level.getEntity(packet.id()) instanceof ItemEntity itemEntity) {
            this.pickupcard$magnetBefore = itemEntity.getItem().copy();
            this.pickupcard$magnetEntityId = packet.id();
        }
    }

    /**
     * 消费端：把"同步前的完整副本 + 同步后的数量"交给 relay。
     * <p>
     * 【id 不匹配就放弃】两次同步之间可能插进别的实体的包（同一次方法调用只碰一个实体，
     * 但 before 捕获是"最后一个 ItemEntity 同步"留下的）—— id 对不上说明这份 before 不是
     * 这个实体的，宁可漏报不可误报。
     * <p>
     * 【消费即复位】两个状态字段不管 relay 要不要弹卡都必须清掉：字段挂在 listener 实例上
     * 跟着连接走，残留的旧 before 会让下一次无关同步撞上过期数据。
     * <p>
     * 【实体不在就不弹】TAIL 时实体已被移除（网络竞态）读不到 after —— 与
     * {@link PickupRelay#onTakeItem} 同一条纪律：宁可漏报不可误报。
     */
    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void pickupcard$magnetRelayAfter(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (packet.id() != this.pickupcard$magnetEntityId || this.pickupcard$magnetBefore == null) {
            return;
        }
        ItemStack before = this.pickupcard$magnetBefore;
        this.pickupcard$magnetBefore = null;
        this.pickupcard$magnetEntityId = -1;
        if (this.level.getEntity(packet.id()) instanceof ItemEntity itemEntity) {
            PickupRelay.onMagnetSync(this.level, packet.id(), before, itemEntity.getItem().getCount());
        }
    }

    @Inject(method = "handleTakeItemEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER))
    private void pickupcard$onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        PickupRelay.onTakeItem(level, packet);
    }

    // =====================================================================
    // 【磁铁确认的第二信号（2026-09-25）】"我的背包槽被服务端改写过"。
    // 【为什么需要它】磁铁吸收信号的归属确认靠"背包增量"，但 SB 这类背包 mod 把物品
    // 吸进的是<b>背包容器的 NBT</b>（挂在玩家背包某个槽上）—— 原版 41 格里"背包还是
    // 那一个背包"，数量永远不变，增量确认永远等不到（真机全灭的根因）。而服务端改完
    // 背包 NBT 必然把那个槽同步下来（{@code handleContainerSetSlot} / 整包
    // {@code handleContainerContent}，containerId = PLAYER_INVENTORY）—— 它就是
    // "有东西进了我这边"的通用客户端信号。
    // 【注入点】TAIL：ensureRunningOnSameThread 之后才是主线程，且原版已把内容应用
    // （此时标记得更晚一拍也无妨 —— 确认看的是 tick 号不是内容）。
    // =====================================================================

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void pickupcard$onContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        PickupRelay.onContainerSync(packet.getContainerId());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void pickupcard$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        PickupRelay.onContainerSync(packet.getContainerId());
    }

    /**
     * 原版放拾取音的那一处 {@code ClientLevel#playLocalSound}：静音名单命中时整条跳过。
     * <p>
     * 【方法体为什么是空的】这里不是"换个音量再放"，是"这次调用不存在"。redirect 方法
     * 的返回值必须与目标调用一致，{@code playLocalSound} 是 void，所以什么都不返回。
     * <p>
     * 【require=2 / allow=2 各钉住什么】这个方法里 {@code playLocalSound} 有<b>两处</b>调用
     * （经验球与物品各一次，字节码核对过），数字写死是有意的：它钉的是 1.20.1 那个方法的
     * 真实形状，改了它就该有人回来看这里。三个属性的实际职责（读的是 Mixin 0.8.5 的
     * {@code InjectionInfo}，不是猜）：
     * <ul>
     *   <li>{@code require=2} = <b>下界</b>，真正会 fail-loud 的那条：匹配不足 2 处就是
     *       {@code Critical injection failure}（口径变化/被同类 mod 抢了节点时启动即炸）。
     *       {@code expect} 的默认值是 1，且它的检查被 {@code DEBUG_INJECTORS} 门控（默认关），
     *       所以别指望它 —— 这里写 {@code expect=2} 只是把意图写在注解上。</li>
     *   <li>{@code allow=2} = <b>上界</b>：不写就是 {@code Integer.MAX_VALUE}，将来这个方法
     *       多出第三处放音会被<b>静默</b>接到同一个闸门上（若单次调用响两声，第二声读到的是
     *       已复位的 false，就只压掉一半）。写上以后，形状一变就是构建红。</li>
     * </ul>
     * <p>
     * 【时序】{@code pickupcard$onTakeItemEntity} 在 {@code ensureRunningOnSameThread} 之后先跑，
     * 已经把判定写进 {@link PickupSoundGate}；原版随后才走到这两处放音（字节码里两处由
     * 一个 if/else 二选一，单次调用只会响一声）。读和写在同一次方法调用、同一条线程上，
     * 见 {@link PickupSoundGate} 的类注释。
     */
    @Redirect(method = "handleTakeItemEntity", expect = 2, require = 2, allow = 2,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void pickupcard$mutePickupSound(ClientLevel level, double x, double y, double z,
                                            SoundEvent sound, SoundSource source,
                                            float volume, float pitch, boolean distanceDelay) {
        if (PickupSoundGate.consumeMute()) {
            return;
        }
        level.playLocalSound(x, y, z, sound, source, volume, pitch, distanceDelay);
    }
}
