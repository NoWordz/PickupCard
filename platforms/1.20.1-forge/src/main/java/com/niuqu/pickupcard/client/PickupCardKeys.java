package com.niuqu.pickupcard.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 键位。默认 K 打开配置界面。
 *
 * <p>【为什么需要一个键位】没有入口的配置界面等于不存在 —— 玩家不可能为了改一个圆角
 * 去手改 TOML。键位可以在"选项 → 控制"里被玩家改掉，这也是原版的规矩。
 */
@Mod.EventBusSubscriber(modid = "pickupcard", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PickupCardKeys {

    /** 打开配置界面。 */
    public static final KeyMapping CONFIG = new KeyMapping(
            "key.pickupcard.config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.misc");

    /** 打开卡片一览网格（A-19 第三个形态试点）。 */
    public static final KeyMapping GRID = new KeyMapping(
            "key.pickupcard.grid", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.misc");

    private PickupCardKeys() {
    }

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(CONFIG);
        event.register(GRID);
    }
}
