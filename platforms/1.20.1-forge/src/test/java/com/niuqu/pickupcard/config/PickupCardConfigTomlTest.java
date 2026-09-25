package com.niuqu.pickupcard.config;

import com.electronwill.nightconfig.toml.TomlFormat;
import com.niuqu.pickupcard.layout.LayoutSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOML 读写层的钉子：0.2.3 的新档名与 sway 键在<b>真 spec</b> 上进得来、回得去。
 *
 * <p>【为什么不用假 spec】Forge 对未知枚举串的防御在它自己的 correct 通道里
 * （loadConfig 时把不合法的值改回默认并落日志）。要钉住"未知档名回落默认而不是炸"，
 * 就得走生产同款路径：解析 TOML → {@code spec.acceptConfig(...)} → 读键。
 * 离线可跑 —— spec 构造与 night-config 解析都不需要 Minecraft。
 *
 * <p>【老配置文件兼容】0.2.2 及更早的文件没有 swayEnabled 这一行 —— 缺键必须安全落
 * false（{@code BooleanValue.define("swayEnabled", false)} 的默认兜底），不能让老玩家的
 * 配置文件一升级就炸。
 */
class PickupCardConfigTomlTest {

    /** 把一段 TOML 解析后挂到真 spec 上 —— 生产里由 FML 在 loadConfig 时做同一件事。 */
    private static void load(String toml) {
        PickupCardConfig.SPEC.acceptConfig(TomlFormat.instance().createParser().parse(toml));
    }

    @AfterEach
    void resetTouchedKeys() {
        // 挂上的配置会留在 JVM 里影响同一轮的其它测试 —— 走前把碰过的键放回出厂值
        if (PickupCardConfig.SPEC.isLoaded()) {
            PickupCardConfig.VALUES.appearMode.set(PickupCardConfig.VALUES.appearMode.getDefault());
            PickupCardConfig.VALUES.exitMode.set(PickupCardConfig.VALUES.exitMode.getDefault());
            PickupCardConfig.VALUES.swayEnabled.set(PickupCardConfig.VALUES.swayEnabled.getDefault());
        }
    }

    /** 新档名 + sway=true 写进 TOML → 读回还是它们（sway 往返保真）。 */
    @Test
    void newAnimationModesAndSwayRoundTrip() {
        load("""
                [layout]
                appearMode = "BOUNCE"
                exitMode = "SCALE"
                swayEnabled = true
                """);
        assertEquals(LayoutSettings.Appear.BOUNCE, PickupCardConfig.VALUES.appearMode.get());
        assertEquals(LayoutSettings.Exit.SCALE, PickupCardConfig.VALUES.exitMode.get());
        assertTrue(PickupCardConfig.layoutSnapshot().swayEnabled(), "sway=true 必须经由 layoutSnapshot 原样读回");
    }

    /** 未知档名字符串：回落出厂默认，不能炸（手改 TOML / 跨版本乱串是常态）。 */
    @Test
    void unknownModeNamesFallBackToDefaults() {
        load("""
                [layout]
                appearMode = "SOMETHING_NEW_FROM_THE_FUTURE"
                exitMode = "SPIN"
                swayEnabled = true
                """);
        assertEquals(LayoutSettings.Appear.SLIDE, PickupCardConfig.VALUES.appearMode.get(),
                "未知入场档名应回落出厂默认");
        assertEquals(LayoutSettings.Exit.TRAIN, PickupCardConfig.VALUES.exitMode.get(),
                "未知消失档名应回落出厂默认");
    }

    /** 0.2.2 的老文件：没有 swayEnabled 这一行 → 安全落 false；老档名照常解析。 */
    @Test
    void oldFileWithoutSwayKeyStaysOff() {
        load("""
                [layout]
                appearMode = "CLIP"
                exitMode = "WIPE"
                """);
        assertEquals(LayoutSettings.Appear.CLIP, PickupCardConfig.VALUES.appearMode.get());
        assertEquals(LayoutSettings.Exit.WIPE, PickupCardConfig.VALUES.exitMode.get());
        assertFalse(PickupCardConfig.layoutSnapshot().swayEnabled(), "老配置文件缺 sway 键 → 默认关");
    }
}
