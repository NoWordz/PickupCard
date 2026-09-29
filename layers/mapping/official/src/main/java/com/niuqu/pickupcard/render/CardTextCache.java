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
 * <p>【缓存分两层（2026-09-29）】单键整块失效有一个被静态分析抓到的盲区：截断预算
 * 里有 {@code maxWidth/scale}（屏宽比例上限是<b>屏幕像素</b>，不随卡缩放），而缩放
 * 档位切换后有 340ms 的平滑动画（{@code CardStage#scaleMove}），期间 scale 每帧都变
 * —— 单键设计下<b>屏上每张卡每帧整块重算</b>，最贵的 {@code getHoverName()} 与翻译
 * 模板正则全被拖着陪跑，而这俩根本不依赖缩放。所以拆成：
 * <ul>
 *   <li><b>内容层</b>：名字原文、数量文本与它们的宽度。键 = 卡片引用、数量、滚动旧值、
 *       显示物品 ID、数量口径两键 —— 全部与缩放无关，一张卡一生重算个位数次；</li>
 *   <li><b>截断层</b>：省略号截断结果与宽度。键 = 内容层变化、缩放、屏宽、名字宽度
 *       上限、截断公式读的四个样式值。340ms 缩放动画里只重算这一小块（两次 font 扫描）。</li>
 * </ul>
 * 【缓存怎么失效】任何一个键变了就重算所在层 —— 不做增量，免得"部分失效"长成第二套真相。
 * 两层各自的累计重算次数挂在 {@link #contentRecomputes}/{@link #fitRecomputes}，
 * 是 harness 断言「缩放动画不重算内容」的观察口（测试纪律：Font 不可离线替身，
 * 行为契约由 dev smoke 钉）。
 *
 * <p>【为什么挂在 {@link CardView} 上】它是"屏幕上的这一次表演"的派生数据，
 * 与 {@code lastBumpAt} 同类：账本不知道、也不该知道。挂上去之后一条卡一个，
 * 卡片被摘掉时缓存跟着一起没，不需要另开一张 map 去剪枝。
 */
public final class CardTextCache {

    /** 名字被截断时补在末尾的省略号（与 {@code CardMetrics} 同一份语义）。 */
    private static final String ELLIPSIS = "…";

    // ---- 内容层键：任一变化就重算名字与数量文本（全部与缩放无关） ----
    private Inbox.Card card;
    private int count = Integer.MIN_VALUE;
    private int prevCount = Integer.MIN_VALUE;
    private boolean showId;
    // 【口径两键（评审 🟡-2，2026-09-25）】countText 读 countFormat/countMode（CardCanvas.countText），
    // 不进键的话玩家在配置界面切「数字含义/数量写法」，屏上活着的卡会拿旧口径文本撑到
    // 其它键变化或自然退场（~3s）。countMode 的语义翻转（+号有无、数字含义）尤其刺眼。
    private com.niuqu.pickupcard.text.CountFormat countFormat;
    private com.niuqu.pickupcard.text.CountMode countMode;

    // ---- 截断层键：内容层变化 或 下列任一变化就重算截断 ----
    private float scale = -1f;
    private int guiWidth = -1;
    private int nameMaxWidth = -1;
    // 截断公式（update 里 room/fixed 那两行）从样式里读的只有这四个数；
    // StyleModel 每秒重读会换实例，逐值比较才不会跟着它每秒白算一次。
    private float styleGap = -1f;
    private float styleBoxHeight = -1f;
    private float stylePaddingH = -1f;
    private float styleBarWidth = -1f;

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

    /** 内容层累计重算次数。harness 遥测/断言用：缩放动画期间它必须不涨（读数口，别当控制流写）。 */
    long contentRecomputes;
    /** 截断层累计重算次数。同上；缩放动画的 340ms 里它涨、内容层不涨，就是分层生效。 */
    long fitRecomputes;

    /**
     * 按当前这一帧的输入刷新（键没变的那层是空操作）。<b>幂等</b>：一帧里调用几次都一样，
     * 布局与绘制各调一次不必互相避让。
     */
    public void update(CardCanvas canvas, Font font, CardView view) {
        Inbox.Card current = view.notice().payload();
        // 数量口径在这里定：拾取数走账本，总数走物品栏实时表（经验/溢出卡在 displayCount 里回账本数）
        int nowCount = canvas.displayCount(view);
        int oldCount = view.prevCount();
        var settings = canvas.settings();
        boolean nowShowId = settings.showItemId();
        var nowCountFormat = settings.countFormat();
        var nowCountMode = settings.countMode();

        // ---- 内容层 ----
        boolean contentChanged = current != card || nowCount != count || oldCount != prevCount
                || nowShowId != showId || nowCountFormat != countFormat || nowCountMode != countMode;
        if (contentChanged) {
            contentRecomputes++;
            card = current;
            count = nowCount;
            prevCount = oldCount;
            showId = nowShowId;
            countFormat = nowCountFormat;
            countMode = nowCountMode;

            name = rawName(current, nowCount, nowShowId);
            countText = canvas.countText(nowCount);
            // 旧值从 canvas.prevCountText 同源取：总数口径下它恒 null —— 账本不知道"上一次合并前
            // 背包里有几个"，硬凑旧值会从旧拾取数滚到新总数，两个口径接不上、画出来就是错数
            prevText = canvas.prevCountText(view);
            countTextWidth = font.width(countText);
            countWidth = prevText == null ? countTextWidth : Math.max(countTextWidth, font.width(prevText));
        }

        // ---- 截断层 ----
        // 【内容层变了必须跟着算】数量文本宽度（fixed 的一项）与名字原文都可能是新值；
        // 只比对截断键自己的话，合并变宽数量那一次会拿着旧预算画旧截断。
        float nowScale = canvas.scale();
        int nowWidth = canvas.guiWidth();
        int nowNameMax = settings.nameMaxWidth();
        StyleModel style = canvas.style();
        if (!contentChanged
                && scale == nowScale && guiWidth == nowWidth && nameMaxWidth == nowNameMax
                && style.gap() == styleGap && style.boxHeight() == styleBoxHeight
                && style.paddingH() == stylePaddingH && style.barWidth() == styleBarWidth) {
            return;
        }
        fitRecomputes++;
        scale = nowScale;
        guiWidth = nowWidth;
        nameMaxWidth = nowNameMax;
        styleGap = style.gap();
        styleBoxHeight = style.boxHeight();
        stylePaddingH = style.paddingH();
        styleBarWidth = style.barWidth();

        // 名字的预算：先扣掉"名字之外固定要占的宽"，剩下的才是名字能用的。
        // 全部在"未缩放单位"里算 —— 与 CardMetrics 的口径逐字一致（那边是唯一出处，
        // 这里只是把 font.width 换成缓存值，公式一个字没改）。
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
