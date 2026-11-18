package com.niuqu.pickupcard.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

public final class MinecraftItemData {
    private MinecraftItemData() {}

    public static String strictKey(ItemStack stack, String id) {
        return stack.getTag() == null ? id : id + '#' + stack.getTag();
    }

    public static void clearIdentityKeys() {}

    public static boolean hasCustomName(ItemStack stack) {
        return stack.hasCustomHoverName();
    }

    public static void setCustomName(ItemStack stack, Component name) {
        stack.setHoverName(name);
    }

    public static void addPreviewGlint(ItemStack stack) {
        stack.enchant(Enchantments.SHARPNESS, 1);
    }

    public static ResourceLocation resource(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
