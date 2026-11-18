package com.niuqu.pickupcard.client;

import com.niuqu.pickupcard.compat.RarityCoreBridge;
import com.niuqu.pickupcard.config.CardGridScreen;
import com.niuqu.pickupcard.config.PickupCardConfig;
import com.niuqu.pickupcard.config.PickupCardConfigScreen;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.pickup.PickupRelay;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class ClientLifecycle {
    private final Set<String> reportedDrops = new HashSet<>();

    public ClientLifecycle(boolean rarityCorePresent, Consumer<String> log) {
        Inbox.INSTANCE.setSources(PickupCardConfig::snapshot, PickupCardConfig::filterSnapshot);
        Inbox.INSTANCE.setDropReporter(item -> reportDrop(item, log));
        CardStage.INSTANCE.setLayoutSource(PickupCardConfig::layoutSnapshot);
        CardStage.INSTANCE.setStyleSources(PickupCardConfig::theme, PickupCardConfig::styleOverrides);
        RarityCoreBridge.install(rarityCorePresent);
    }

    private void reportDrop(CardContent.Item item, Consumer<String> log) {
        String id = BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
        if (reportedDrops.add(id)) {
            log.accept("拾取 " + id + " 没有弹卡：命中 config/pickupcard-client.toml 的 [filter] blacklist。"
                    + "删除这条规则或加入 whitelist 即可放行。");
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        PickupRelay.onClientTick(mc.player);
        CardStage.INSTANCE.tick();
        while (PickupCardKeys.CONFIG.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new PickupCardConfigScreen(null));
            }
        }
        while (PickupCardKeys.GRID.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new CardGridScreen(null));
            }
        }
    }

    public static void leaveWorld() {
        Inbox.INSTANCE.reset();
        CardStage.INSTANCE.clear();
    }
}
