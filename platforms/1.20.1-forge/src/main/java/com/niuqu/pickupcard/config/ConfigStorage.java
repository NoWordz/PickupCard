package com.niuqu.pickupcard.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

/** Loader-specific schema builder. Setting definitions are shared in PickupCardConfig. */
public final class ConfigStorage {
    private final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
    private ForgeConfigSpec spec;

    public static void register(FMLJavaModLoadingContext container) {
        container.registerConfig(ModConfig.Type.CLIENT, PickupCardConfig.STORAGE.spec());
    }

    ForgeConfigSpec spec() {
        if (spec == null) {
            spec = builder.build();
        }
        return spec;
    }

    ConfigStorage comment(String... lines) {
        builder.comment(lines);
        return this;
    }

    void push(String section) {
        builder.push(section);
    }

    void pop() {
        builder.pop();
    }

    ConfigValue<Boolean> define(String name, boolean defaults) {
        return wrap(builder.define(name, defaults));
    }

    ConfigValue<String> define(String name, String defaults) {
        return wrap(builder.define(name, defaults));
    }

    ConfigValue<Integer> defineInRange(String name, int defaults, int min, int max) {
        return wrap(builder.defineInRange(name, defaults, min, max));
    }

    ConfigValue<Long> defineInRange(String name, long defaults, long min, long max) {
        return wrap(builder.defineInRange(name, defaults, min, max));
    }

    ConfigValue<Double> defineInRange(String name, double defaults, double min, double max) {
        return wrap(builder.defineInRange(name, defaults, min, max));
    }

    <E extends Enum<E>> ConfigValue<E> defineEnum(String name, E defaults) {
        return wrap(builder.defineEnum(name, defaults));
    }

    ConfigValue<List<? extends String>> defineList(String name, List<String> defaults) {
        return wrap(builder.defineList(name, defaults, value -> value instanceof String));
    }

    private <T> ConfigValue<T> wrap(ForgeConfigSpec.ConfigValue<T> value) {
        return new ConfigValue<>(value::get, value::set, value::getDefault);
    }
}
