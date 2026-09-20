package com.niuqu.pickupcard.layout;

import com.niuqu.pickupcard.style.CubicBezier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardMoveTest {

    @Test
    void newCardLandsImmediately() {
        CardMove move = new CardMove();
        assertEquals(40f, move.y("a", 40f, 1_000L), 1e-4f, "新卡不滑进来");
    }

    @Test
    void doesNotJumpWhenTheTargetChanges() {
        CardMove move = new CardMove();
        move.y("a", 40f, 1_000L);
        // 被新卡顶上去了：起跑这一帧必须还在原位
        assertEquals(40f, move.y("a", 80f, 2_000L), 1e-4f);
    }

    @Test
    void settlesExactlyOnTarget() {
        CardMove move = new CardMove();
        move.y("a", 40f, 1_000L);
        move.y("a", 80f, 2_000L);
        assertEquals(80f, move.y("a", 80f, 2_000L + (long) CardMove.DURATION_MS + 1L), 1e-4f);
    }

    /** 中途走的必须是那条 340ms 曲线（{@link CubicBezier#SLOT}），不是线性的、也不是别的缓动。 */
    @Test
    void followsTheDraftCurve() {
        CardMove move = new CardMove();
        move.y("a", 0f, 1_000L);
        move.y("a", 100f, 2_000L);
        long half = 2_000L + (long) (CardMove.DURATION_MS / 2f);
        float expected = 100f * CubicBezier.SLOT.at(0.5f);
        assertEquals(expected, move.y("a", 100f, half), 0.05f);
        // 这条曲线前段很快：走一半时间应该已经过了九成路（线性只会到 50）
        assertEquals(true, move.y("a", 100f, half) > 90f);
    }

    @Test
    void retainsOnlyLiveCards() {
        CardMove move = new CardMove();
        move.y("a", 0f, 1_000L);
        move.y("b", 0f, 1_000L);
        move.y("a", 50f, 1_100L);
        move.retain(Set.of("a"));
        // b 被忘掉之后重新出现，应该当新卡直接落位
        assertEquals(90f, move.y("b", 90f, 1_200L), 1e-4f);
    }
}
