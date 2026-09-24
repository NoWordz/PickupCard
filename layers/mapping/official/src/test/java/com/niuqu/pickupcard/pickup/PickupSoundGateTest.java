package com.niuqu.pickupcard.pickup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静音判定的接力棒。
 * <p>
 * 【为什么值得钉】这条链的坏法全都不报错：写下"压"之后没被读走（盒子不干净）、
 * 读的时候不带复位（下一次拾取被上一次的决定吃掉）、或者复位的方向写反（该压的没压）。
 * 三样在游戏里的表现都是"静音名单时灵时不灵"，而那种问题从画面和日志里都看不出来 ——
 * 它只是一条声音该不该响。纯状态，不必起游戏。
 */
class PickupSoundGateTest {

    @Test
    void nothingIsArmedByDefault() {
        // 清一次，避免别的用例留下的状态影响它（JUnit 同一个 JVM，静态状态是共享的）
        PickupSoundGate.consumeMute();
        assertFalse(PickupSoundGate.consumeMute(), "没人写下决定时，放音不该被压");
    }

    @Test
    void armedMuteIsReadOnce() {
        PickupSoundGate.arm(true);
        assertTrue(PickupSoundGate.consumeMute(), "写下的'压'要能被读到");
        assertFalse(PickupSoundGate.consumeMute(), "读走就该复位，否则下一次拾取会被误压");
    }

    @Test
    void armedFalseDoesNotMute() {
        PickupSoundGate.arm(false);
        assertFalse(PickupSoundGate.consumeMute(), "'不压' 读出来就是不压");
        assertFalse(PickupSoundGate.consumeMute(), "读完之后仍然是'不压'");
    }

    @Test
    void armingFalseClearsAPreviousMute() {
        // 上一条拾取写了 true 却没走到放音那一步（实体已被移除之类），
        // 下一条拾取必须能把残留清掉，否则它会莫名其妙地被压掉一次。
        PickupSoundGate.arm(true);
        PickupSoundGate.arm(false);
        assertFalse(PickupSoundGate.consumeMute(), "新的一次拾取要盖掉上一次的残留");
    }
}
