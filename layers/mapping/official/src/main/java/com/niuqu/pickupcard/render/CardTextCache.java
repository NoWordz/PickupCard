package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 一张卡的<b>文本度量备忘</b>：名字、数量文本、截断结果与它们的宽度。
 *
 * <p>【为什么需要它】这些值在卡片的一生里几乎不变，而每一帧要被问<b>两次</b>
 * （布局量宽一次、绘制一次），每次问都要重新付这些钱：
 * <ul>
 *   <li>{@code ItemStack#getHoverName()} —— 每次调用都<b>新建</b>
 *       {@code MutableComponent + TranslatableContents}，而 {@code TranslatableContents}
 *       的翻译缓存挂在实例上（以 {@code Language} 实例为失效键）—— 实例每次新建，
 *       缓存就<b>永远失效</b>，于是每帧重跑一遍模板正则与分解；</li>
 *   <li>{@code Font#width(String)} —— 原版<b>没有字符串宽度缓存</b>，逐码点累加，
 *       每个字符还要各查两次字体表；</li>
 *   <li>{@code Font#plainSubstrByWidth} —— 截断时从 0 重扫，不复用刚算出来的宽度。</li>
 * </ul>
 * 2026-09-20 的性能轮实测：把物品名整条关掉，稳态「文字」段从 470~849us 掉到
 * 172~294us、layout 从 56~94us 掉到 23us —— 名字这条路就是稳态最贵的一块。
 *
 * <p>【缓存怎么失效】全部输入做成键：卡片对象引用、当前数量、滚动旧值、缩放、
 * 屏宽（决定名字预算）、玩家设的名字宽度上限、显示物品名/显示物品 ID 两个开关。
 * 任何一个变了就整块重算 —— 不做增量，免得"部分失效"长成第二套真相。
 * 名字与数量在卡片合并、玩家改配置、切屏时才会变，所以命中率接近 100%。
 *
 * <p>【为什么挂在 {@link CardView} 上】它是"屏幕上的这一次表演"的派生数据，
 * 与 {@code lastBumpAt} 同类：账本不知道、也不该知道。挂上去之后一条卡一个，
 * 卡片被摘掉时缓存跟着一起没，不需要另开一张 map 去剪枝。
 */
public final class CardTextCache {

    /** 名字被截断时补在末尾的省略号（与 {@code CardMetrics} 同一份语义）。 */
    private static final String ELLIPSIS = "…";

    // ---- 键：任一变化就整块重算 ----
    private Inbox.Card card;
    private int count = Integer.MIN_VALUE;
    private int prevCount = Integer.MIN_VALUE;
    private float scale = -1f;
    private int guiWidth = -1;
    private int nameMaxWidth = -1;
    private boolean showName;
    private boolean showId;

    // ---- 值 ----
    /** 未截断的名字（含「显示物品 ID」与溢出卡的两条分支）。 */
    private String name = "";
    /** 这一帧画的那个名字（超预算就截断补省略号）。 */
    private String fitted = "";
    private int fittedWidth;
    private String countText = "";
    /** 数字滚动中要画的旧值；没在滚 = null。 */
    private String prevText;
    /** 数量占位宽：滚动中取新旧两个里宽的那个。 */
    private int countWidth;
    /** 只算新值时的数量宽度 —— 名字的截断预算用它（与 {@code namelessWidth} 同式）。 */
    private int countTextWidth;

    /**
     * 按当前这一帧的输入刷新（键没变就是空操作）。<b>幂等</b>：一帧里调用几次都一样，
     * 布局与绘制各调一次不必互相避让。
     */
    public void update(CardCanvas canvas, Font font, CardView view) {
        Inbox.Card current = view.notice().payload();
        int nowCount = view.notice().count();
        int oldCount = view.prevCount();
        float nowScale = canvas.scale();
        int nowWidth = canvas.guiWidth();
        var settings = canvas.settings();
        int nowNameMax = settings.nameMaxWidth();
        boolean nowShowName = settings.showItemName();
        boolean nowShowId = settings.showItemId();
        if (current == card && nowCount == count && oldCount == prevCount && nowScale == scale
                && nowWidth == guiWidth && nowNameMax == nameMaxWidth
                && nowShowName == showName && nowShowId == showId) {
            return;
        }
        card = current;
        count = nowCount;
        prevCount = oldCount;
        scale = nowScale;
        guiWidth = nowWidth;
        nameMaxWidth = nowNameMax;
        showName = nowShowName;
        showId = nowShowId;

        name = rawName(current, nowCount, nowShowId);
        countText = canvas.countText(nowCount);
        prevText = oldCount == nowCount ? null : canvas.countText(oldCount);
        countTextWidth = font.width(countText);
        countWidth = prevText == null ? countTextWidth : Math.max(countTextWidth, font.width(prevText));

        // 名字的预算：先扣掉"名字之外固定要占的宽"，剩下的才是名字能用的。
        // 全部在"未缩放单位"里算 —— 与 CardMetrics 的口径逐字一致（那边是唯一出处，
        // 这里只是把 font.width 换成缓存值，公式一个字没改）。
        StyleModel style = canvas.style();
        float gap = style.gap();
        float fixed = style.barWidth() + gap + style.boxHeight() + gap
                + style.paddingH() * 2f + gap + countTextWidth;
        float room = Math.max(0f, CardMetrics.maxWidth(canvas) / nowScale - fixed);
        if (nowNameMax > 0) {
            room = Math.min(room, nowNameMax / nowScale);
        }
        if (font.width(name) <= room) {
            fitted = name;
            fittedWidth = font.width(name);
        } else {
            int budget = (int) Math.max(0f, room - font.width(ELLIPSIS));
            fitted = font.plainSubstrByWidth(name, budget) + ELLIPSIS;
            fittedWidth = font.width(fitted);
        }
    }

    /**
     * 卡上显示的名字原文（未截断）。经验卡没有 ItemStack，走翻译键。
     * <p>与 {@code CardMetrics#displayName} 同一套分支；溢出卡的名字带数量，
     * 所以它在这里而那边留了个不带数的兜底。
     */
    private static String rawName(Inbox.Card card, int count, boolean showId) {
        if (card.content() instanceof CardContent.Item item) {
            if (showId) {
                return net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(item.stack().getItem()).toString();
            }
            return item.stack().getHoverName().getString();
        }
        if (card.content() instanceof CardContent.Overflow) {
            return Component.translatable("pickupcard.overflow", count).getString();
        }
        return Component.translatable("pickupcard.xp").getString();
    }

    /** 这一帧该画的名字（已截断）。 */
    public String fitted() {
        return fitted;
    }

    /** 名字的实测宽度（镜像时右对齐要用它）。 */
    public int fittedWidth() {
        return fittedWidth;
    }

    /** 数量文本（当前值）。 */
    public String countText() {
        return countText;
    }

    /** 数字滚动中的旧值；没在滚 = null。 */
    public String prevText() {
        return prevText;
    }

    /** 数量占位宽（滚动中取新旧里宽的那个）。 */
    public int countWidth() {
        return countWidth;
    }

    /** 当前数量文本自身的宽度（滚动时画新值那一行要用它）。 */
    public int countTextWidth() {
        return countTextWidth;
    }
}
