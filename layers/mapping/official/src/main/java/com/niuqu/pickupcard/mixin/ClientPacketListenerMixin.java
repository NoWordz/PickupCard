package com.niuqu.pickupcard.mixin;

import com.niuqu.pickupcard.pickup.PickupRelay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
 * 【这个类为什么不带 @OnlyIn】它住在映射层，要同时编给 Forge 与 NeoForge —— 而
 * {@code @OnlyIn} 的包名在两边不同（{@code net.minecraftforge...} / {@code net.neoforged...}）。
 * 客户端专属由 mixin 配置只在客户端加载来保证，不靠这个注解。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleTakeItemEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER))
    private void pickupcard$onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        PickupRelay.onTakeItem(level, packet);
    }
}
