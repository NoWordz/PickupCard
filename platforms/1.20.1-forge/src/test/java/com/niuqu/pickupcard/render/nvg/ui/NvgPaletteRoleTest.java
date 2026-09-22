package com.niuqu.pickupcard.render.nvg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.tokens.Tokens;
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
    }

    /** 故意不用的角色：角色名 → 为什么不用。 */
    private static final Map<String, String> UNUSED_ROLES = new LinkedHashMap<>();

    static {
        UNUSED_ROLES.put("BORDER_STRONG", "界面没有「强描边」这一档：现在只有一条 1px 的边");
        UNUSED_ROLES.put("TEXT_DISABLED", "「灰掉不可点」那一档**已经存在**，只是借的是 TEXT_SECONDARY"
                + "（NvgButton 的 action==null、NvgColorChip 的无效色块）—— 缺的是专属色，不是缺那一档");
        UNUSED_ROLES.put("DANGER", "危险色还没上：删除按钮现在用的是普通文字色");
        UNUSED_ROLES.put("FOCUS_RING", "焦点环还没画 —— 与框架侧同一条欠账（A-11 的记录里点名了）");
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
        NvgPalette palette = NvgPalette.dark(StyleModel.Accents.defaults());
        assertEquals(4f, palette.radius, 0.001f, "Radius.MD(2u) × u(2)");
        assertEquals(1f, palette.outlineWidth, 0.001f,
                "Size.HAIRLINE：绝对 1px，**不乘 u**（细线不该随屏幕放大）");
        assertEquals(5f, palette.knobRadius, 0.001f, "Size.KNOB_RADIUS(2.5u) × u(2)");
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
        expected.add("backdrop");      // 唯一一个"框架里没有对应角色"的，见它的 javadoc
        assertEquals(expected, fields,
                "宿主的颜色字段与对应表对不上：新加字段要给角色，删字段要从表里去掉");
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
