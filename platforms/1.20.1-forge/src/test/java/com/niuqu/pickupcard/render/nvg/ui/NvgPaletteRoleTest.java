package com.niuqu.pickupcard.render.nvg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.tokens.Units;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「两套值、一套角色」的离线钉子（A-13）。
 *
 * <p>【它在守什么】配色口径是：**角色跟着框架走、值由本项目给**（已发布外观不动）。
 * 但"跟着走"这件事光写在注释里会腐烂 —— 框架哪天加一个颜色角色（比如说 `WARNING`），
 * 没人会记得回来处理。所以下面这张表把它变成**可执行**的：
 * {@link #everyTrellisRoleIsAccountedFor} 会反射 `Tokens.Color` 的全部常量，
 * 任何一个既没被映射、也没写明"为什么不用"的角色都会让它红。
 *
 * <p>【加角色时该怎么办】要么在 {@link #ROLE_TO_FIELD} 里给它一个字段（并在 {@code NvgPalette}
 * 上实现它），要么进 {@link #UNUSED_ROLES} 并写清理由 —— **理由不是走过场**：
 * "还没做"和"不需要"是两回事，下一个人要靠这句话判断能不能删。
 */
class NvgPaletteRoleTest {

    /** Trellis 颜色角色 → 宿主字段名。**唯一的一份对应表**（`NvgPalette` 的 javadoc 指向这里）。 */
    private static final Map<String, String> ROLE_TO_FIELD = new LinkedHashMap<>();

    static {
        ROLE_TO_FIELD.put("SURFACE", "panel");
        ROLE_TO_FIELD.put("SURFACE_RAISED", "well");
        // ⚠️ 这两个是"角色对得上、机制不同"：框架用"底色 + 白色叠加层"，我们用三个绝对色。
        ROLE_TO_FIELD.put("OVERLAY_HOVER", "wellHover");
        ROLE_TO_FIELD.put("OVERLAY_PRESS", "wellPressed");
        ROLE_TO_FIELD.put("BORDER", "outline");
        ROLE_TO_FIELD.put("TEXT_PRIMARY", "text");
        ROLE_TO_FIELD.put("TEXT_SECONDARY", "textDim");
        // 值是数据驱动的（按稀有度从卡面主题取），所以它只是**角色**上归框架。
        ROLE_TO_FIELD.put("ACCENT", "accent");
        // A-15 起真的被画了：框架基类画环（画法与几何归框架），值由宿主给
        // （浅色主题下框架那个亮青在近白底上读不出来）。
        ROLE_TO_FIELD.put("FOCUS_RING", "focusRing");
    }

    /** 故意不用的角色：角色名 → 为什么不用。 */
    private static final Map<String, String> UNUSED_ROLES = new LinkedHashMap<>();

    static {
        UNUSED_ROLES.put("BORDER_STRONG", "界面没有「强描边」这一档：现在只有一条 1px 的边");
        UNUSED_ROLES.put("TEXT_DISABLED", "「灰掉不可点」那一档**已经存在**，只是借的是 TEXT_SECONDARY"
                + "（NvgButton 的 action==null、NvgColorChip 的无效色块）—— 缺的是专属色，不是缺那一档");
        UNUSED_ROLES.put("DANGER", "危险色还没上：删除按钮现在用的是普通文字色");
    }

    /**
     * <b>宿主自己的</b>颜色字段：框架里没有对应角色，所以不在这张对应表里。
     *
     * <p>每一条都要写清"为什么框架里没有" —— 和 {@link #UNUSED_ROLES} 一个道理：
     * 不写的话下一个人分不清"故意不加"和"忘了加"。
     */
    private static final Map<String, String> HOST_OWN_FIELDS = new LinkedHashMap<>();

    static {
        HOST_OWN_FIELDS.put("backdrop", "界面底色：框架不知道「压在游戏画面上」这件事");
        HOST_OWN_FIELDS.put("knobActive", "圆钮的亮面：框架的图层里没有「圆钮」（它是控件自己的形状）");
        HOST_OWN_FIELDS.put("knobIdle", "圆钮的沉面：同上");
    }

    @Test
    @DisplayName("框架的每个颜色角色都被交代过：要么映射到字段，要么写明为什么不用")
    void everyTrellisRoleIsAccountedFor() {
        Set<String> roles = colorRoles();
        assertFalse(roles.isEmpty(),
                "一个都没反射到 —— 那这条测试等于没测（Tokens.Color 的字段形状变了？）");

        Set<String> unaccounted = new TreeSet<>(roles);
        unaccounted.removeAll(ROLE_TO_FIELD.keySet());
        unaccounted.removeAll(UNUSED_ROLES.keySet());
        assertTrue(unaccounted.isEmpty(),
                "框架的颜色角色 " + unaccounted + " 还没交代：在 ROLE_TO_FIELD 里给它一个字段，"
                        + "或者放进 UNUSED_ROLES 并写清为什么不用");
    }

    @Test
    @DisplayName("对应表指向的字段都真实存在，而且确实是颜色（int）")
    void mappedFieldsExistAndAreColors() throws Exception {
        for (Map.Entry<String, String> entry : ROLE_TO_FIELD.entrySet()) {
            Field field = NvgPalette.class.getDeclaredField(entry.getValue());
            assertEquals(int.class, field.getType(),
                    "角色 " + entry.getKey() + " 映射到的 " + entry.getValue() + " 不是颜色字段");
        }
    }

    @Test
    @DisplayName("尺寸来自 token × u：圆角 4、描边 1（不乘 u）、滑块半径 5")
    void sizesComeFromTokens() {
        // A-14：u 由调用方按画布高算好传进来。这里显式用 240 画布（u=1.5）那一档 ——
        // 就是为了证明**调色板尺寸真的跟着 u 缩**（从前烤死在 BASE，u 变了它纹丝不动）。
        NvgPalette palette = NvgPalette.dark(StyleModel.Accents.defaults(), Units.u(240f));
        float u = Units.u(240f);
        assertEquals(1.5f, u, 0.001f, "240 在地板线之下 → 撞 MIN");
        assertEquals(Tokens.Radius.MD * u, palette.radius, 0.001f, "Radius.MD(2u) × u(1.5) = 3");
        assertEquals(Tokens.Size.KNOB_RADIUS * u, palette.knobRadius, 0.001f,
                "Size.KNOB_RADIUS(2.5u) × u(1.5) = 3.75");
        // 基准档对照：u=2 时才是 4 / 5。
        NvgPalette atBase = NvgPalette.dark(StyleModel.Accents.defaults(), Tokens.Unit.BASE);
        assertEquals(4f, atBase.radius, 0.001f, "基准档 Radius.MD × 2");
        assertEquals(5f, atBase.knobRadius, 0.001f, "基准档 KNOB_RADIUS × 2");
        // 这两条**不乘 u**：细线看起来该多细是"看得清"的事，屏幕大一号不该变粗。
        assertEquals(1f, palette.outlineWidth, 0.001f, "Size.HAIRLINE：绝对 1px");
        assertEquals(3f, palette.trackThickness, 0.001f, "细条厚度（滑条轨道 / 滚动条共用）");
        assertEquals(1.5f, palette.trackRadius, 0.001f, "细条圆角 = 厚度的一半");
    }

    @Test
    @DisplayName("A-15：焦点环有值了，而且深色用框架的基准值、浅色压暗（同一支青）")
    void focusRingHasAValuePerTheme() {
        NvgPalette dark = NvgPalette.dark(StyleModel.Accents.defaults(), Tokens.Unit.BASE);
        NvgPalette light = NvgPalette.light(StyleModel.Accents.defaults(), Tokens.Unit.BASE);

        assertEquals(Tokens.Color.FOCUS_RING, dark.focusRing,
                "深色主题直接用框架的基准值（色号归框架那一档）");
        assertTrue((dark.focusRing >>> 24) != 0, "深色的环得有 alpha，否则基类不画（alpha==0 = 不画）");
        assertTrue((light.focusRing >>> 24) != 0, "浅色的环也得画得出来");

        // 关键的那条：浅色主题必须换一个值 —— 框架那个亮青在近白底上读不出来。
        assertTrue(dark.focusRing != light.focusRing,
                "浅色主题跟深色用了同一个环色 —— 近白底上读不出来的正是这一条要挡的");
    }

    @Test
    @DisplayName("双向：宿主每个颜色字段也都报了角色（新增/删除字段必须回来改表）")
    void colorFieldsAndRolesMatchBothWays() {
        Set<String> fields = new TreeSet<>();
        for (Field field : NvgPalette.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;      // `U` 是 float、构造器参数不算
            }
            fields.add(field.getName());
        }
        Set<String> expected = new TreeSet<>(ROLE_TO_FIELD.values());
        expected.addAll(HOST_OWN_FIELDS.keySet());   // 宿主自己的角色（框架里没有对应 token）
        assertEquals(expected, fields,
                "宿主的颜色字段与对应表对不上：新加字段要给角色（框架的进 ROLE_TO_FIELD、"
                        + "自己的进 HOST_OWN_FIELDS 并写清为什么框架里没有），删字段要从表里去掉");
    }

    /** 反射出 `Tokens.Color` 的常量名（= 框架的颜色角色集合）。 */
    private static Set<String> colorRoles() {
        Set<String> roles = new TreeSet<>();
        for (Field field : Tokens.Color.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                roles.add(field.getName());
            }
        }
        return roles;
    }
}
