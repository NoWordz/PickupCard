package com.niuqu.pickupcard.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.HashMap;
import java.util.Map;

public final class MinecraftItemData {
    // Vanilla exposes a copy-on-write snapshot, including network-only and removed components.
    // Give each equal patch one key for the current session without serializing it on pickup.
    private static final Map<DataComponentPatch, Long> PATCH_IDS = new HashMap<>();
    private static long nextPatchId;

    private MinecraftItemData() {}

    public static String strictKey(ItemStack stack, String id) {
        if (stack.isComponentsPatchEmpty()) {
            return id;
        }
        long patchId = PATCH_IDS.computeIfAbsent(stack.getComponentsPatch(), patch -> ++nextPatchId);
        return id + "#components/" + patchId;
    }

    public static void clearIdentityKeys() {
        PATCH_IDS.clear();
    }

    public static boolean hasCustomName(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_NAME);
    }

    public static void setCustomName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    public static void addPreviewGlint(ItemStack stack) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SHARPNESS), 1);
        }
    }

    public static ResourceLocation resource(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
