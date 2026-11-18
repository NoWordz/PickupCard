package com.niuqu.pickupcard.compat;

import com.niuqu.pickupcard.notice.MergeMode;
import com.niuqu.pickupcard.pickup.ItemIdentity;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MinecraftItemDataTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sameComponentsMergeDespiteDifferentCountAndInsertionOrder() {
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD, 1);
        first.set(DataComponents.DAMAGE, 7);
        MinecraftItemData.setCustomName(first, Component.literal("Mine"));

        ItemStack second = new ItemStack(Items.DIAMOND_SWORD, 2);
        MinecraftItemData.setCustomName(second, Component.literal("Mine"));
        second.set(DataComponents.DAMAGE, 7);

        assertEquals(ItemIdentity.strictKeyOf(first), ItemIdentity.strictKeyOf(second));
    }

    @Test
    void strictModeSeparatesNonPersistentComponents() {
        ItemStack locking = new ItemStack(Items.FILLED_MAP);
        locking.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
        ItemStack scaling = new ItemStack(Items.FILLED_MAP);
        scaling.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);

        assertNotEquals(ItemIdentity.strictKeyOf(locking), ItemIdentity.strictKeyOf(scaling));
    }

    @Test
    void strictModeSeparatesCustomNamesAndDamage() {
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack named = plain.copy();
        MinecraftItemData.setCustomName(named, Component.literal("Mine"));
        ItemStack damaged = named.copy();
        damaged.set(DataComponents.DAMAGE, 7);

        assertNotEquals(ItemIdentity.strictKeyOf(plain), ItemIdentity.strictKeyOf(named));
        assertNotEquals(ItemIdentity.strictKeyOf(named), ItemIdentity.strictKeyOf(damaged));
        assertEquals(ItemIdentity.strictKeyOf(named), ItemIdentity.keyOf(named, MergeMode.SAME_NBT));
    }
}
