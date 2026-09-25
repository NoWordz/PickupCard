package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.text.CountFormat;
import com.niuqu.pickupcard.text.CountMode;

import javax.annotation.Nullable;

/**
 * 一帧的绘制上下文：这一帧所有卡共用的东西。
 * <p>
 * 【为什么不让 painter 自己读配置】painter 每帧、每卡都要问"现在入场进度多少"，
 * 如果它各自去读配置、各自去算时间，就会出现同一帧里两张卡用了不同时刻的配置
 * （主题热重读正好卡在中间时）。在一处采样一次、往下传，这类不一致从结构上消失。
 * <p>
 * 【为什么把进度的计算放在这里】{@link CardView} 只记时刻，{@link CardTimeline} 只会算，
 * 把两者接起来需要"当前时刻"——那是这一帧的属性，不是卡或时间轴的。放这里，
 * painter 写起来就是 {@code canvas.enterOf(slot.view())}，不用自己拼三个参数。
 *
 * @param now      本帧时刻（毫秒）
 * @param timeline 入场/跳动时间轴（来自主题）
 * @param style    当前主题（已 sanitize）
 * @param settings 当前会话参数
 * @param layout   锚点与展开方式（行为参数，来自 TOML）
 * @param guiWidth  GUI 逻辑宽度
 * @param guiHeight GUI 逻辑高度
 * @param scale     本帧生效的卡片缩放（1.0 = 100%）。布局用它算卡的屏幕尺寸，
 *                  绘制用它决定 pose / NanoVG 变换 —— 两边必须是同一个数
 */
public record CardCanvas(long now,
                         CardTimeline timeline,
                         StyleModel style,
                         PickupCardSettings settings,
                         LayoutSettings layout,
                         int guiWidth,
                         int guiHeight,
                         float scale) {

    /** 入场进度 ∈ [0,1]，1 = 已就位。 */
    public float enterOf(CardView view) {
        return timeline.enter(now, view.notice().bornAt());
    }

    /** 竖条自身的展开进度 ∈ [0,1]。 */
    public float barOf(CardView view) {
        return timeline.bar(now, view.notice().bornAt());
    }

    /** 内容滑出的进度 ∈ [0,1]；入场位移与缩放都用它。 */
    public float contentOf(CardView view) {
        return timeline.content(now, view.notice().bornAt());
    }

    /**
     * 卡上数字该显示的量：拾取口径 = 账本数量；总数口径 = 物品栏持有总数。
     * <p>【为什么经验卡与溢出卡回账本数量】它们没有"背包里的那个物品"可数 ——
     * 经验卡数的是经验点，溢出卡数的是"第几项"，总数口径对两者没有定义。
     * <p>【为什么 CardTextCache 不需要新失效机制】这里返回的数就是文本缓存的键之一
     * —— 总数一变键就变，缓存自然重算；粒度是 tick（{@link InventoryTotals}），
     * 对"实时跟随"足够。
     */
    public int displayCount(CardView view) {
        if (settings.countMode() == CountMode.TOTAL
                && view.notice().payload().content() instanceof CardContent.Item item) {
            return InventoryTotals.of(item.stack().getItem());
        }
        return view.notice().count();
    }

    /** 数字跳动进度 ∈ [0,1]，1 = 无缩放。 */
    public float bumpOf(CardView view) {
        return timeline.bump(now, view.lastBumpAt());
    }

    /**
     * 整张卡的脉冲倍率（1 = 不缩）。合并那一刻整张卡"鼓"一下，峰值见
     * {@code --pc-bump-peak}。
     * <p>【为什么整张卡都要动，而不是只动数字】用户 2026-09-17 的原话是
     * "相同物品，再次拾起后却没有更显眼的动画"。数字那一小块本来就只有几个像素高，
     * 它自己弹一下在余光里几乎看不见；<b>整张卡的轮廓</b>才是余光能认出来的东西。
     * <p>【和入场缩放的区别】入场是"从竖条后面滑出来"（位移 + 淡入），脉冲是原地放大再回来，
     * 两者的形状完全不同，所以同一个物品第二次被捡到时不会被误认成新卡。
     */
    public float pulseOf(CardView view) {
        return Easing.pulse(bumpOf(view), style.bumpPeak());
    }

    /** 数字滚动进度 ∈ [0,1]；1 = 已经滚到新值（或根本没滚动）。 */
    public float rollOf(CardView view) {
        return bumpOf(view);
    }

    /**
     * 滚动中要画的那个"旧数字"；没在滚动时返回 null。
     * <p>【总数口径恒 null】账本只记得"这次合并前捡了几个"，不知道"合并前背包里有几个"
     * —— 硬拿账本旧值凑，数字滚动会从旧<b>拾取数</b>滚到新<b>总数</b>，画出来的两个数
     * 口径不同，看着就是数错了。滚动禁掉，跳动 bump（由合并事件驱动）照旧。
     */
    @Nullable
    public String prevCountText(CardView view) {
        if (settings.countMode() == CountMode.TOTAL) {
            return null;
        }
        if (view.prevCount() == view.notice().count()) {
            return null;
        }
        return countText(view.prevCount());
    }

    /**
     * 这一帧数字要占多宽：滚动中取旧值/新值里宽的那个。
     * <p>【为什么不能只看新值】宽度是排布算出来的，而排布在动画之前 —— 只按新值算的话，
     * 1 → 10 这一下会在滚到一半时把卡整个撑宽，看着像卡在抖。
     */
    public int countWidth(CardView view, net.minecraft.client.gui.Font font) {
        String prev = prevCountText(view);
        int w = font.width(countText(view.notice().count()));
        return prev == null ? w : Math.max(w, font.width(prev));
    }

    /** 退场进度 ∈ [0,1]，0 = 还没退场。 */
    public float exitOf(CardView view) {
        if (!view.exiting()) return 0f;
        return CardTimeline.exit(now, view.exitStartAt(), settings.exitMs());
    }

    /** 淡回进度 ∈ [0,1]，1 = 已经回到全不透明；没在淡回时是 0。 */
    public float reviveOf(CardView view) {
        if (!view.reviving()) {
            return 0f;
        }
        // 时长来自主题（--pc-revive-ms）。从前是 CardTimeline 里一个常量 160ms，
        // 而淡出（exitMs 480）是配置项 —— 回来比离开快 3 倍，读起来就是"凭空冒出来"。
        long ms = style.reviveMs();
        return ms <= 0L ? 1f : Easing.clamp01((now - view.reviveAt()) / (float) ms);
    }

    /**
     * 这一帧该给这张卡的不透明度：退场是 1 → 0；淡回是从「撤消那一刻的 alpha」→ 1。
     * <p>
     * 【为什么两件事写在同一个方法里】外壳（NanoVG 的 {@code nvgGlobalAlpha}）与内容（图标的
     * 调制色、文字的颜色）必须拿到同一个数 —— 分散在两处迟早对不上，而「内容没跟着淡」
     * 正是这类 bug 的样子。
     */
    public float exitAlphaOf(CardView view) {
        if (view.reviving()) {
            // 撤消那一刻的不透明度：按「退场已经播到哪儿」算，跟当时画出的那一帧一致
            float from = 1f - Easing.easeOutCubic(
                    CardTimeline.exit(view.reviveAt(), view.exitStartAt(), settings.exitMs()));
            return from + (1f - from) * Easing.easeOutCubic(reviveOf(view));
        }
        if (!view.exiting()) {
            return 1f;
        }
        return 1f - Easing.easeOutCubic(exitOf(view));
    }

    /**
     * 数量该怎么写：拾取口径带进账符号（`+64` / `×64` / `+1.2K`…），总数口径不带
     * （`64` / `1.2K`…）—— 符号说的是"进账"，持有量不是进账。
     */
    public String countText(int count) {
        CountFormat format = settings.countFormat();
        return settings.countMode() == CountMode.TOTAL ? format.hold(count) : format.gain(count);
    }
}
