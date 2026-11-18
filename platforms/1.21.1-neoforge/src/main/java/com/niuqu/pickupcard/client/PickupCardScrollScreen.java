package com.niuqu.pickupcard.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class PickupCardScrollScreen extends Screen {
    protected PickupCardScrollScreen(Component title) {
        super(title);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return (vertical != 0.0 && scrollVertically(mouseX, mouseY, vertical))
                || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    protected abstract boolean scrollVertically(double mouseX, double mouseY, double delta);
}
