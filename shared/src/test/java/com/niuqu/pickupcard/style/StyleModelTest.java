package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主题解析的钉子：缺键补默认、坏文本不炸、颜色格式宽容。
 * 主题 JSON 是玩家/资源包作者手写的，解析必须"打不坏"。
 */
class StyleModelTest {

    @Test
    void blankFallsBackToDefaults() {
        assertEquals(StyleModel.defaults(), StyleModel.parse(null));
        assertEquals(StyleModel.defaults(), StyleModel.parse("   "));
    }

    @Test
    void brokenJsonFallsBackToDefaults() {
        assertEquals(StyleModel.defaults(), StyleModel.parse("{ not json !!"));
        assertEquals(StyleModel.defaults(), StyleModel.parse("[]"));
    }

    @Test
    void partialOverrideKeepsDefaultsForMissingKeys() {
        StyleModel parsed = StyleModel.parse("{\"geometry\": {\"cornerRadius\": 4}}");
        assertEquals(4, parsed.cornerRadius());
        assertEquals(StyleModel.defaults().paddingH(), parsed.paddingH());
        assertEquals(StyleModel.defaults().enterMs(), parsed.enterMs());
    }

    @Test
    void sixDigitColorGetsFullAlpha() {
        StyleModel parsed = StyleModel.parse("{\"text\": {\"nameColor\": \"#FF0000\"}}");
        assertEquals(0xFFFF0000, parsed.nameColor());
    }

    @Test
    void eightDigitColorIsRRGGBBAA() {
        // 网页习惯 RRGGBBAA：内部统一转 ARGB
        StyleModel parsed = StyleModel.parse("{\"material\": {\"fillTop\": \"#12345680\"}}");
        assertEquals(0x80123456, parsed.fillTop());
    }

    @Test
    void badColorFallsBackToThatKeyDefault() {
        StyleModel parsed = StyleModel.parse("{\"text\": {\"nameColor\": \"#zzz\"}}");
        assertEquals(StyleModel.defaults().nameColor(), parsed.nameColor());
    }

    @Test
    void numbersAreSanitized() {
        StyleModel parsed = StyleModel.parse(
                "{\"geometry\": {\"cornerRadius\": -5, \"iconSize\": 99}, \"material\": {\"glowAlpha\": 999}}");
        assertEquals(0, parsed.cornerRadius());
        assertEquals(64, parsed.iconSize(), "图标边长夹到上限 64");
        assertEquals(255, parsed.glowAlpha());
    }

    @Test
    void switchesCanBeTurnedOff() {
        StyleModel parsed = StyleModel.parse(
                "{\"animation\": {\"enterEnabled\": false, \"bumpEnabled\": false, \"glowPulseEnabled\": false}}");
        assertTrue(!parsed.enterEnabled());
        assertTrue(!parsed.bumpEnabled());
        assertTrue(!parsed.glowPulseEnabled());
    }

    // ---- 强调色：主题里从第一天就写着这些键，但 2026-09-18 之前没人读 ----

    @Test
    void accentsComeFromTheTheme() {
        StyleModel parsed = StyleModel.parse("""
                {"accent": {"common": "#111111", "uncommon": "#222222", "rare": "#333333",
                            "epic": "#444444", "xp": "#555555", "overflow": "#666666"}}""");
        StyleModel.Accents a = parsed.accents();
        assertEquals(0xFF111111, a.common());
        assertEquals(0xFF222222, a.uncommon());
        assertEquals(0xFF333333, a.rare());
        assertEquals(0xFF444444, a.epic());
        assertEquals(0xFF555555, a.xp());
        assertEquals(0xFF666666, a.overflow());
    }

    @Test
    void missingAccentKeysFallBackOneByOne() {
        // 主题文件永远允许只写想改的那几行：写了的生效，没写的回默认
        StyleModel.Accents def = StyleModel.Accents.defaults();
        StyleModel.Accents a = StyleModel.parse("{\"accent\": {\"rare\": \"#010203\"}}").accents();
        assertEquals(0xFF010203, a.rare());
        assertEquals(def.common(), a.common());
        assertEquals(def.overflow(), a.overflow());
    }

    @Test
    void accentsSurviveOverridesAndSanitize() {
        // 覆盖不改强调色（它整套归主题），但夹逼不能把它丢掉
        StyleModel themed = StyleModel.parse("{\"accent\": {\"rare\": \"#0A0B0C\"}}");
        StyleModel applied = StyleOverrides.builder().cornerRadius(9).build().apply(themed);
        assertEquals(0xFF0A0B0C, applied.accents().rare());
        assertEquals(9, applied.cornerRadius());
    }

    @Test
    void nullAccentsCannotReachTheRenderer() {
        // 手搓一个 accents 为空的主题也应被夹逼兜住，而不是让渲染层 NPE
        StyleModel broken = new StyleModel(4, 4, 3, 3, 16, 4, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                true, true, true, null);
        assertEquals(StyleModel.Accents.defaults().rare(), broken.sanitized().accents().rare());
    }
}
