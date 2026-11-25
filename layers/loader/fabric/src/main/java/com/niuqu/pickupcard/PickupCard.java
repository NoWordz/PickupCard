package com.niuqu.pickupcard;

import com.niuqu.pickupcard.client.ClientLifecycle;
import com.niuqu.pickupcard.client.PickupCardKeys;
import com.niuqu.pickupcard.compat.RarityCoreBridge;
import com.niuqu.pickupcard.config.ConfigStorage;
import com.niuqu.pickupcard.dev.DevHarness;
import com.niuqu.pickupcard.render.CardStage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PickupCard implements ClientModInitializer {
    public static final String MOD_ID = "pickupcard";
    public static final Logger LOGGER = LoggerFactory.getLogger("PickupCard");

    private ClientPacketListener connection;

    @Override
    public void onInitializeClient() {
        ConfigStorage.register();
        FabricLoader loader = FabricLoader.getInstance();
        boolean rarityCorePresent = loader.isModLoaded("raritycore");
        new ClientLifecycle(rarityCorePresent, LOGGER::info);

        KeyBindingHelper.registerKeyBinding(PickupCardKeys.CONFIG);
        KeyBindingHelper.registerKeyBinding(PickupCardKeys.GRID);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> CardStage.INSTANCE.renderHud(graphics));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                RarityCoreBridge.warmUp(rarityCorePresent));

        if (loader.isDevelopmentEnvironment()) {
            KeyBindingHelper.registerKeyBinding(DevHarness.OPEN);
            ClientTickEvents.END_CLIENT_TICK.register(client -> DevHarness.tick());
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                    DevHarness.registerCommands(dispatcher));
        }
    }

    private void tick(Minecraft client) {
        // Fabric's disconnect event can run on Netty. Keep the HUD and inbox on the client thread.
        ClientPacketListener current = client.getConnection();
        if (connection != null && connection != current) {
            ClientLifecycle.leaveWorld();
        }
        connection = current;
        ClientLifecycle.tick();
    }
}
