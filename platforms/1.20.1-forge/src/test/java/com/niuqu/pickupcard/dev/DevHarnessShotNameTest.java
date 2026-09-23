package com.niuqu.pickupcard.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * harness 产物名的离线对账（A-31）。
 *
 * <p>【为什么值得单独一条】这条缺陷是<b>静默的</b>：四档 {@code -PharmerGuiScale=1..4}
 * 跑了同名的图，而 {@code clearOldShots} 每轮开头又"删掉所有 {@code pickupcard-*}" ——
 * 于是四档产物互相覆盖，唯一症状是分析脚本读到了另一档的图。A-19 起就记着这条，
 * 一直没修（真机 48–52 轮那四档的图今天只剩最后一档）。
 *
 * <p>修法两半，各自可判：
 * <ol>
 *   <li>产物名带上档标签（{@code -sN}）—— 四档不再同名；</li>
 *   <li>清理只清<b>本轮这一档</b>（外加加标签之前留下的无名旧产物），别把别档删掉。</li>
 * </ol>
 *
 * <p>{@link DevHarness.ShotNaming} 是那两个纯函数住的壳（{@code DevHarness} 本身要 MC 的类，
 * 加载不起来）。名字错是静默的，所以这里逐条钉。
 */
class DevHarnessShotNameTest {

    @Test
    @DisplayName("档标签拼进名字：没有档 → 原样（向后兼容），有档 → base-sN-suffix")
    void scaleTagIsAppended() {
        assertEquals("pickupcard-grid", DevHarness.ShotNaming.name("pickupcard-grid", "", null));
        assertEquals("pickupcard-grid-hover", DevHarness.ShotNaming.name("pickupcard-grid", "", "hover"));

        assertEquals("pickupcard-grid-s3", DevHarness.ShotNaming.name("pickupcard-grid", "-s3", null));
        assertEquals("pickupcard-grid-s3-hover",
                DevHarness.ShotNaming.name("pickupcard-grid", "-s3", "hover"));
    }

    @Test
    @DisplayName("档标签的推导：off / 没施加 → 空；N 且施加了 → -sN（off 必须与旧名字逐字相同）")
    void tagDerivation() {
        assertEquals("", DevHarness.ShotNaming.tagFor("off", true), "没定档 → 不带标签");
        assertEquals("-s1", DevHarness.ShotNaming.tagFor("1", true));
        assertEquals("-s4", DevHarness.ShotNaming.tagFor("4", true));
        assertEquals("", DevHarness.ShotNaming.tagFor("3", false),
                "这一轮根本不会施加缩放（shot 模式）→ 不许打标签，否则文件名撒谎");
        assertEquals("", DevHarness.ShotNaming.tagFor(null, true));
    }

    @Test
    @DisplayName("四档的同一张图，名字两两不同 —— 这是这条缺陷的核心")
    void fourScalesProduceDistinctNames() {
        String a = DevHarness.ShotNaming.name("pickupcard-grid", "-s1", "hover");
        String b = DevHarness.ShotNaming.name("pickupcard-grid", "-s2", "hover");
        String c = DevHarness.ShotNaming.name("pickupcard-grid", "-s3", "hover");
        String d = DevHarness.ShotNaming.name("pickupcard-grid", "-s4", "hover");
        assertFalse(a.equals(b) || a.equals(c) || a.equals(d) || b.equals(c) || b.equals(d) || c.equals(d),
                "不同档不许同名：/s1/hover 与 /s2/hover 覆盖了就再也分不出哪张是哪档");
    }

    @Test
    @DisplayName("未定档时清理全部（与旧行为一致）；定了档只清本轮那一档")
    void clearScopeFollowsTheTag() {
        // 未定档：pickupcard-* 全算本轮（旧行为，一字不变）
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-grid", ""));
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-config-hover", ""));
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-hud-enter3", ""));
        assertFalse(DevHarness.ShotNaming.belongsToRun("other-mod-shot", ""), "非本 mod 的图不许删");
        assertFalse(DevHarness.ShotNaming.belongsToRun(null, ""));

        // 定了档 -s3：只有本档的算本轮，别的档不许清
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-grid-s3", "-s3"));
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-grid-s3-hover", "-s3"));
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-hud-s3-enter3", "-s3"));
        assertFalse(DevHarness.ShotNaming.belongsToRun("pickupcard-grid-s1-hover", "-s3"),
                "别档的图必须留着 —— 就是这里从前把四档删成一档");
        assertFalse(DevHarness.ShotNaming.belongsToRun("pickupcard-grid-s4", "-s3"));

        // 加档标签之前的旧产物（无档标签）：定着档也一并清掉，否则会与新图混在一起
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-grid-hover", "-s3"),
                "无档标签的旧产物该清（它们是加标签之前留下的，留着会与新一轮混帧）");
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-config", "-s3"));
    }

    @Test
    @DisplayName("档标签不许误伤后缀里带 s 数字的名字（如 cycle 序号）")
    void scaleTagDoesNotMatchUnrelatedNumbers() {
        // "pickupcard-config-config-cycle1" 里没有 -sN，属于"旧产物"一类
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-config-config-cycle1", "-s3"));
        // 真实档标签要能认出来
        assertTrue(DevHarness.ShotNaming.belongsToRun("pickupcard-config-s3-look", "-s3"));
    }
}
