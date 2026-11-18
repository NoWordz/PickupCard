package com.niuqu.pickupcard;

import com.niuqu.pickupcard.client.ClientLifecycle;
import com.niuqu.pickupcard.client.PickupCardKeys;
import com.niuqu.pickupcard.compat.RarityCoreBridge;
import com.niuqu.pickupcard.config.ConfigStorage;
import com.niuqu.pickupcard.config.PickupCardConfigScreen;
import com.niuqu.pickupcard.dev.DevHarness;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PickupCard.MOD_ID)
public final class PickupCard {
    public static final String MOD_ID = "pickupcard";
    public static final Logger LOGGER = LoggerFactory.getLogger("PickupCard");

    public PickupCard(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        var modBus = context.getModEventBus();
        ConfigStorage.register(context);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) ->
                        new PickupCardConfigScreen(parent)));

        boolean rarityCorePresent = ModList.get().isLoaded("raritycore");
        new ClientLifecycle(rarityCorePresent, LOGGER::info);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(PickupCardKeys.CONFIG);
            event.register(PickupCardKeys.GRID);
        });

        var gameBus = MinecraftForge.EVENT_BUS;
        gameBus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) ClientLifecycle.tick();
        });
        gameBus.addListener((RenderGuiEvent.Post event) -> CardStage.INSTANCE.renderHud(event.getGuiGraphics()));
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingIn event) ->
                RarityCoreBridge.warmUp(rarityCorePresent));
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientLifecycle.leaveWorld());

        if (!FMLEnvironment.production) {
            modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(DevHarness.OPEN));
            gameBus.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) DevHarness.tick();
            });
            gameBus.addListener((RegisterClientCommandsEvent event) ->
                    DevHarness.registerCommands(event.getDispatcher()));
            LOGGER.info("调试屏已启用：F9 或 /pickupcarddev");
        }
    }
}
