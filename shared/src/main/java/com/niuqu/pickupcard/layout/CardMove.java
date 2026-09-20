package com.niuqu.pickupcard.layout;

import com.niuqu.pickupcard.style.CubicBezier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 卡片换位置时的过渡（"旧的被新卡顶上去"）。
 *
 * <p>【为什么抽成纯逻辑】它只有几个数：上一帧画在哪、什么时候起跑、终点在哪。
 * 留在渲染类里就只能开游戏盯屏幕验，而这种"看起来在动"的东西最容易看起来没问题 ——
 * 抽出来就能拿已知答案钉住：起跑那一帧必须<b>恰好</b>在原位（不能跳），
 * 跑完必须<b>恰好</b>在终点，中途必须走的是那条 340ms 曲线。
 *
 * <p>【新卡不走过渡】设计要求写得很明白："新槽位要直接落在最终位置，不能复用过渡，
 * 否则整张卡会从别处滑进来"。新卡该是"从隧道口长出来"，不是"飘过来"。
 */
public final class CardMove {

    /** 过渡时长。位置、缩放、卡宽三样共用这一条 340ms。 */
    public static final float DURATION_MS = 340f;

    private record Move(float from, float target, long startAt) {
    }

    private final Map<String, Float> drawn = new HashMap<>();
    private final Map<String, Move> moving = new HashMap<>();

    /** 这一帧该把这张卡画在哪。 */
    public float y(String key, float target, long now) {
        Move move = moving.get(key);
        if (move != null && Math.abs(move.target() - target) < 0.001f) {
            float t = (now - move.startAt()) / DURATION_MS;
            if (t >= 1f) {
                moving.remove(key);
                drawn.put(key, target);
                return target;
            }
            float value = move.from() + (target - move.from()) * CubicBezier.SLOT.at(t);
            drawn.put(key, value);
            return value;
        }

        Float previous = drawn.get(key);
        if (previous == null || Math.abs(previous - target) < 0.001f) {
            moving.remove(key);
            drawn.put(key, target);
            return target;
        }

        // 目标变了：从"现在画在哪"起跑，不跳
        moving.put(key, new Move(previous, target, now));
        return previous;
    }

    /** 只读：这张卡当前画在哪（没上过屏返回 null）。给诊断读数用 —— "重叠与否"只有数字能定案。 */
    public Float drawnY(String key) {
        return drawn.get(key);
    }

    /** 忘掉已经不在屏上的卡，免得账越记越长。 */
    public void retain(Set<String> liveKeys) {
        drawn.keySet().retainAll(liveKeys);
        moving.keySet().retainAll(liveKeys);
    }
}
