package com.niuqu.pickupcard;

import com.niuqu.pickupcard.client.ClientLifecycle;
import com.niuqu.pickupcard.client.PickupCardKeys;
import com.niuqu.pickupcard.compat.RarityCoreBridge;
import com.niuqu.pickupcard.config.ConfigStorage;
import com.niuqu.pickupcard.config.PickupCardConfigScreen;
import com.niuqu.pickupcard.dev.DevHarness;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = PickupCard.MOD_ID, dist = Dist.CLIENT)
public final class PickupCard {
    public static final String MOD_ID = "pickupcard";
    public static final Logger LOGGER = LoggerFactory.getLogger("PickupCard");

    public PickupCard(IEventBus modBus, ModContainer container) {
        ConfigStorage.register(container);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new PickupCardConfigScreen(parent));

        boolean rarityCorePresent = ModList.get().isLoaded("raritycore");
        new ClientLifecycle(rarityCorePresent, LOGGER::info);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(PickupCardKeys.CONFIG);
            event.register(PickupCardKeys.GRID);
        });

        var gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener((ClientTickEvent.Post event) -> {
            ClientLifecycle.tick();
            // Slider previews read the in-memory values immediately; persist once after the drag.
            if (!Minecraft.getInstance().mouseHandler.isLeftPressed()) {
                ConfigStorage.flushPending();
            }
        });
        gameBus.addListener((GameShuttingDownEvent event) -> ConfigStorage.flushPending());
        gameBus.addListener((RenderGuiEvent.Post event) -> CardStage.INSTANCE.renderHud(event.getGuiGraphics()));
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingIn event) ->
                RarityCoreBridge.warmUp(rarityCorePresent));
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientLifecycle.leaveWorld());

        if (!FMLEnvironment.production) {
            modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(DevHarness.OPEN));
            gameBus.addListener((ClientTickEvent.Post event) -> {
                DevHarness.tick();
            });
            gameBus.addListener((RegisterClientCommandsEvent event) ->
                    DevHarness.registerCommands(event.getDispatcher()));
            LOGGER.info("调试屏已启用：F9 或 /pickupcarddev");
        }
    }
}
