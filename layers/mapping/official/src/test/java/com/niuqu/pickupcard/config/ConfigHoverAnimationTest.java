package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 行悬停淡入淡出的离线对账（A-30）。
 *
 * <p>【为什么值得单独一条】这一小段动画以前住在界面类里，<b>宿主侧零回归网</b>：A-30 的评审
 * 用变异证明过 —— 把 {@code nowMs * 1_000_000L} 那个乘数去掉（毫秒当纳秒，动画静默快 1000 倍），
 * <b>宿主 42 个测试类全绿</b>。单位错是静默的，只有肉眼能发现。把驱动搬到 {@link ConfigRows.Row}
 * 之后它可离线测了，这条就是那张网。
 *
 * <p>【它同时钉两件事】
 * <ol>
 *   <li><b>单位</b>：110ms 的淡入，在 55ms 处必须走到一半附近；乘错 1e6 的话那一刻早就到 1 了。</li>
 *   <li><b>重申幂等</b>：宿主每帧都会 {@code driveHover} 一次（"指针还在这一行吗"），
 *       目标没变时不许重开 —— 否则动画永远停在起点（这正是 A-30 收敛 {@code Tween} 的那个语义）。</li>
 * </ol>
 */
class ConfigHoverAnimationTest {

    private static ConfigRows.Row row() {
        return new ConfigRows.Row("label", null, "hint");
    }

    @Test
    @DisplayName("淡入时长就是 HOVER_IN_MS：一半时间走一半路（单位乘错这里立刻红）")
    void fadeInTakesTheExpectedTime() {
        ConfigRows.Row r = row();
        r.driveHover(true, 0L);

        assertEquals(0f, r.hoverValueAt(0L), 1e-4f, "起步那一帧还是 0");
        float mid = r.hoverValueAt(ConfigRows.Row.HOVER_IN_MS / 2);
        assertTrue(mid > 0.3f && mid < 0.9f,
                "110ms 的淡入在 55ms 应当走在中段（easeOutCubic 前快后慢），实际 " + mid
                        + " —— 到 1 说明时间被乘了 1e6，几乎没动说明除以了");
        assertEquals(1f, r.hoverValueAt(ConfigRows.Row.HOVER_IN_MS), 1e-4f, "到点就是 1");
    }

    @Test
    @DisplayName("每帧重申同一个目标（宿主就是这么调的）不会把动画按回起点")
    void repeatedDriveDoesNotRestartTheFade() {
        ConfigRows.Row r = row();
        // 复刻渲染循环：每 16ms 推一次目标、随后取一次值
        for (long t = 0; t <= ConfigRows.Row.HOVER_IN_MS; t += 16) {
            r.driveHover(true, t);
            r.hoverValueAt(t);
        }
        assertEquals(1f, r.hoverValueAt(ConfigRows.Row.HOVER_IN_MS), 1e-4f,
                "每帧重申同一个目标，淡入仍该走完 —— 重开的话它永远停在起点附近");
    }

    @Test
    @DisplayName("掉头用淡出时长：指针离开后往 0 走，用的是 HOVER_OUT_MS")
    void fadeOutUsesItsOwnDuration() {
        ConfigRows.Row r = row();
        r.driveHover(true, 0L);
        r.hoverValueAt(ConfigRows.Row.HOVER_IN_MS);   // 先亮满
        assertEquals(1f, r.hoverValueAt(ConfigRows.Row.HOVER_IN_MS), 1e-4f);

        r.driveHover(false, ConfigRows.Row.HOVER_IN_MS);
        long halfOut = ConfigRows.Row.HOVER_IN_MS + ConfigRows.Row.HOVER_OUT_MS / 2;
        float mid = r.hoverValueAt(halfOut);
        assertTrue(mid > 0.05f && mid < 0.95f, "应该在往回走的半路，实际 " + mid);
        assertEquals(0f, r.hoverValueAt(ConfigRows.Row.HOVER_IN_MS + ConfigRows.Row.HOVER_OUT_MS),
                1e-4f, "淡出到点就是 0");
    }
}
