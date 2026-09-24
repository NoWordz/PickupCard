package com.niuqu.pickupcard.filter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 过滤优先级的全部钉子：白名单压黑名单、静音正交、坏规则跳过、**默认什么都不丢**。
 * 这些规则一旦在游戏里才发现错，玩家看到的是"我明明加了白名单为什么不弹"——
 * 那种问题从截图里看不出来，只能在这里拦。
 */
class FilterRulesTest {

    private static FilterSubject subject(String id, String mod, String... tags) {
        return new FilterSubject(id, mod, Set.of(tags));
    }

    private static FilterSettings settings(List<String> black, List<String> white,
                                           List<String> mute) {
        return new FilterSettings(black, white, mute);
    }

    // ---- 解析 ----

    @Test
    void parseAcceptsTheThreeRuleKinds() {
        assertNotNull(FilterRule.parse("minecraft:cobblestone"));
        assertNotNull(FilterRule.parse("#forge:ores"));
        assertNotNull(FilterRule.parse("@somebotania"));
        assertNotNull(FilterRule.parse("  #forge:ores  "));
    }

    @Test
    void parseRejectsBrokenRules() {
        assertNull(FilterRule.parse(null));
        assertNull(FilterRule.parse(""));
        assertNull(FilterRule.parse("   "));
        assertNull(FilterRule.parse("#"));
        assertNull(FilterRule.parse("@"));
        // 不带命名空间的物品 id 拒绝：宁可让玩家写全，也不悄悄猜
        assertNull(FilterRule.parse("cobblestone"));
    }

    @Test
    void ruleMatchingRespectsKind() {
        var cobble = subject("minecraft:cobblestone", "minecraft", "forge:cobblestones");
        assertTrue(FilterRule.parse("minecraft:cobblestone").matches(cobble));
        assertTrue(FilterRule.parse("#forge:cobblestones").matches(cobble));
        assertTrue(FilterRule.parse("@minecraft").matches(cobble));
        assertFalse(FilterRule.parse("#forge:ores").matches(cobble));
        assertFalse(FilterRule.parse("minecraft:stone").matches(cobble));
    }

    // ---- 优先级 ----

    @Test
    void whitelistBeatsBlacklist() {
        var decision = FilterRules.check(
                subject("minecraft:iron_ingot", "minecraft"),
                settings(List.of("minecraft:iron_ingot"), List.of("minecraft:iron_ingot"),
                        List.of()));
        assertTrue(decision.show());
        assertTrue(decision.emphasized());
    }

    @Test
    void blacklistDrops() {
        var decision = FilterRules.check(
                subject("minecraft:dirt", "minecraft"),
                settings(List.of("minecraft:dirt"), List.of(), List.of()));
        assertFalse(decision.show());
    }

    @Test
    void nothingIsDroppedByDefault() {
        // 【这条测试是为什么存在的】默认忽略表被删掉了：以前泥土/圆石/沙子会被静默丢弃，
        // 玩家的观感是"我捡了东西什么都没弹"，和"mod 坏了"一模一样。
        // 现在默认每一次拾取都弹卡 —— 想安静是玩家自己往黑名单里写。
        for (String id : List.of("minecraft:dirt", "minecraft:cobblestone", "minecraft:sand",
                "minecraft:gravel", "minecraft:netherrack", "minecraft:wheat_seeds",
                "minecraft:diamond", "minecraft:stone")) {
            var decision = FilterRules.check(subject(id, "minecraft"), FilterSettings.defaults());
            assertTrue(decision.show(), id + " 默认应当弹卡");
            assertFalse(decision.emphasized(), id + " 默认不该被强调");
        }
    }

    @Test
    void muteListFlagsWithoutDropping() {
        var decision = FilterRules.check(
                subject("minecraft:iron_ingot", "minecraft"),
                settings(List.of(), List.of(), List.of("minecraft:iron_ingot")));
        assertTrue(decision.show());
        assertFalse(decision.emphasized());
        assertTrue(decision.muted());
    }

    @Test
    void muteAndWhitelistAreOrthogonal() {
        var decision = FilterRules.check(
                subject("minecraft:beacon", "minecraft"),
                settings(List.of(), List.of("minecraft:beacon"), List.of("minecraft:beacon")));
        assertTrue(decision.show());
        assertTrue(decision.emphasized());
        assertTrue(decision.muted());
    }

    @Test
    void blacklistDropsWithoutClaimingToMuteSound() {
        // 【为什么要专门钉这一条】`Decision.muted()` 的语义是"连原版拾取音一起压掉"，
        // 而账本会把丢弃那一支的判定原样交给声音侧。黑名单的语义只是"不弹卡"——
        // 东西还是玩家捡到的，声音该留。这里一旦变成 true，往黑名单里写一样东西
        // 会连它的拾取音一起吃掉，而画面上完全看不出原因。
        var dropped = FilterRules.check(
                subject("minecraft:dirt", "minecraft"),
                settings(List.of("minecraft:dirt"), List.of(), List.of("minecraft:dirt")));
        assertFalse(dropped.show());
        assertFalse(dropped.muted(), "黑名单只丢掉卡片，不该顺手把拾取音也压了");
        assertFalse(FilterRules.Decision.DROP.muted(), "DROP 的静音位恒为假");
        assertFalse(FilterRules.Decision.PLAIN.muted(), "PLAIN 的静音位恒为假");
    }

    @Test
    void mutedTracksTheMuteListOnly() {
        // muted 位是一条独立的事实：只有"命中静音名单"才为真。白色、普通、丢弃三种都必须是假。
        record Case(String id, List<String> black, List<String> white, List<String> mute,
                    boolean wantMuted) {
        }
        var cases = List.of(
                new Case("minecraft:stone", List.of(), List.of(), List.of("minecraft:stone"), true),
                new Case("minecraft:stone", List.of(), List.of(), List.of(), false),
                new Case("minecraft:stone", List.of(), List.of("minecraft:stone"), List.of(), false),
                new Case("minecraft:stone", List.of("minecraft:stone"), List.of(), List.of(), false),
                // tag 与 mod 写法同样能点亮静音位
                new Case("somebotania:mana_diamond", List.of(), List.of(),
                        List.of("#forge:gems"), true),
                new Case("somebotania:mana_diamond", List.of(), List.of(),
                        List.of("@somebotania"), true));
        for (Case c : cases) {
            var decision = FilterRules.check(subject(c.id(), c.id().split(":")[0],
                            "forge:gems"), settings(c.black(), c.white(), c.mute()));
            assertEquals(c.wantMuted(), decision.muted(), () -> "静音位错了：" + c);
        }
    }

    @Test
    void tagRuleHitsEverythingWithTag() {
        var decision = FilterRules.check(
                subject("somebotania:mana_diamond", "somebotania", "forge:gems", "c:gems"),
                settings(List.of("#forge:gems"), List.of(), List.of()));
        assertFalse(decision.show());
    }

    @Test
    void brokenRulesAreSkippedNotFatal() {
        var decision = FilterRules.check(
                subject("minecraft:dirt", "minecraft"),
                settings(List.of("dirt", "#", "@", "!!", "minecraft:dirt"), List.of(), List.of()));
        assertFalse(decision.show());
    }

    @Test
    void unfilteredSubjectPassesPlain() {
        assertEquals(FilterRules.Decision.PLAIN,
                FilterRules.check(subject("minecraft:diamond", "minecraft"),
                        FilterSettings.defaults()));
    }
}
