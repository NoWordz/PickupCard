package com.niuqu.pickupcard.magnet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MagnetMath#absorbed} 的账：一次实体数据同步里"被吸走了几个"。
 * <p>
 * 【信号形态】磁铁/漏斗吸物品 = 服务端把 ItemEntity 的数量改小 → 客户端收到数据同步。
 * 判据只有一条：<b>旧多新少</b>。新多（奇怪的合成/合并？）与不变都不是吸取——
 * 宁可漏报不可误报，弹一张不该弹的卡比少弹一张烦得多（用户验收口径）。
 */
class MagnetMathTest {

    @Test
    @DisplayName("整组吸走：64 → 0 = 64")
    void fullAbsorption() {
        assertEquals(64, MagnetMath.absorbed(64, 0));
    }

    @Test
    @DisplayName("部分吸取：64 → 30 = 34")
    void partialAbsorption() {
        assertEquals(34, MagnetMath.absorbed(64, 30));
    }

    @Test
    @DisplayName("数量变多不是吸取（不弹卡）：30 → 64 = 0")
    void growthIsNotAbsorption() {
        assertEquals(0, MagnetMath.absorbed(30, 64));
    }

    @Test
    @DisplayName("数量不变不是吸取：64 → 64 = 0")
    void noChangeIsNothing() {
        assertEquals(0, MagnetMath.absorbed(64, 64));
    }

    @Test
    @DisplayName("前后皆空没有吸取：0 → 0 = 0")
    void emptyBeforeIsNothing() {
        assertEquals(0, MagnetMath.absorbed(0, 0));
    }

    @Test
    @DisplayName("同步前是空的（刚生成的实体第一次同步）不算吸取：0 → 64 = 0")
    void spawnSyncIsNotAbsorption() {
        // 实体刚生成时客户端本没有旧值，mixin 层 before 为 null 根本不会走到这；
        // 但"before=0"作为防御边界钉死：0 张卡也弹不出。
        assertEquals(0, MagnetMath.absorbed(0, 64));
    }
}
