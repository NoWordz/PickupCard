package com.niuqu.pickupcard.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

import java.util.List;
import java.util.Objects;

/** Loader-specific schema builder. Setting definitions are shared in PickupCardConfig. */
public final class ConfigStorage {
    private final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
    private ModConfigSpec spec;
    private boolean dirty;

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, PickupCardConfig.STORAGE.spec());
    }

    public static void flushPending() {
        ConfigStorage storage = PickupCardConfig.STORAGE;
        if (storage.dirty && storage.spec().isLoaded()) {
            storage.spec().save();
            storage.dirty = false;
        }
    }

    ModConfigSpec spec() {
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
        return wrap(builder.defineListAllowEmpty(name, defaults, () -> "", value -> value instanceof String));
    }

    private <T> ConfigValue<T> wrap(ModConfigSpec.ConfigValue<T> value) {
        return new ConfigValue<>(() -> spec().isLoaded() ? value.get() : value.getDefault(), updated -> {
            if (!Objects.equals(value.get(), updated)) {
                value.set(updated);
                dirty = true;
            }
        }, value::getDefault);
    }
}
