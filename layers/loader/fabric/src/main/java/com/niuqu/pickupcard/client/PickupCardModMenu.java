package com.niuqu.pickupcard.client;

import com.niuqu.pickupcard.config.PickupCardConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class PickupCardModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PickupCardConfigScreen::new;
    }
}
