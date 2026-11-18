package com.niuqu.pickupcard.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** A setting backed by the loader's client TOML. */
record ConfigValue<T>(Supplier<T> getter, Consumer<T> setter, Supplier<T> defaults) {

    T get() {
        return getter.get();
    }

    void set(T value) {
        setter.accept(value);
    }

    T getDefault() {
        return defaults.get();
    }
}
