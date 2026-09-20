package com.niuqu.pickupcard.render.nvg;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.rarity.RarityAccent;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.render.FadingItemBuffers;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.RevealWindow;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import java.util.List;

import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgBoxGradient;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScale;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgTranslate;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;

/**
 * <b>本 mod 唯一的卡面画法。</b>三段式：<pre>[稀有度竖条] [物品图标格] [物品名字 ...... 数量]</pre>
 * 三个独立的圆角矩形，等高；稀有度只体现在最左那根竖条上。
 *
 * <h2>为什么只剩一条路</h2>
 * 2026-09-17 之前，同一张卡面有四份实现：原版渲染、DOM 草稿当贴图、SDF 着色器图层、
 * NanoVG 外壳。用户的原话是"我们的项目不干净"。代价是实测出来的：
 * <ul>
 *   <li>修"内容穿透竖条"那个 bug 时，裁剪补进了三条路径、<b>漏了第四条</b>
 *       （NanoVG 的影子批没有窗口），用户第二遍才报回来；</li>
 *   <li>同一轮里 SDF 回退那条还把<b>竖条自己</b>裁掉了。</li>
 * </ul>
 * 同一个几何写 N 遍，就一定会有 N-1 遍是错的。所以这里把绘制收到一处：
 * <b>微光、竖条、两个内容框、入场裁剪全在 NanoVG 里</b>，SDF 图层与它的着色器一起删掉了。
 * <p>
 * <b>投影与顶部高光后来也删了</b>（用户 2026-09-17："直接把影子和高光删了"）——
 * 参数一起从主题里拿掉，理由见 {@link StyleModel} 的类注释。
 *
 * <h2>为什么物品图标与文字还在原版</h2>
 * 它们是 MC 自己拥有的两样东西：物品图标是 3D 模型 + 附魔光效 + 耐久条的渲染结果，
 * 文字是 MC 的字形图集（含中文）。2026-09-19 之前图标曾经被烘成离屏贴图（ItemIconCache，
 * 已删）以换取真 alpha 淡出，但快照冻住了活的东西 —— 附魔光不再滚动、分辨率钉死在贴图、
 * glint 亮条纹烘丢（用户报的"动效丢失/变暗/锯齿/颠倒"四连）。现在的答案：
 * 图标<b>永远每帧原版现渲</b>，退场淡出由 {@link FadingItemBuffers} 换渲染层拿到 ——
 * 病根是那几层不开混合，不是图标不该现渲。
 *
 * <h2>入场动画：竖条先开，内容再出来</h2>
 * <ol>
 *   <li>竖条在自己该在的位置上<b>纵向展开</b>（头 30% 时间），影子跟着涨；</li>
 *   <li>内容随后从竖条右侧<b>滑出来</b>（从 18% 起跑），被隧道口 {@link RevealWindow} 裁住。</li>
 * </ol>
 * 关键不是"滑"这个动作，而是<b>卡片自身尺寸全程不变</b>——是位移，不是把内容拉长。
 *
 * <h2>退场淡出：三条通道一个数</h2>
 * 外壳是矢量（{@code nvgGlobalAlpha}）、图标走换层后的全局色调制（{@code setShaderColor}，
 * 见 {@link FadingItemBuffers}）、文字走颜色里的 alpha —— 三条通道三个 API，
 * 但必须是同一个数（{@link #exitAlphaOf}）。
 */
public final class NvgCardPainter {

    /** 名字被截断时补的那个字符（跟 {@code CardMetrics#ELLIPSIS} 同一个字符）。 */
    private static final String ELLIPSIS = "\u2026";

    /** 稀有度微光往外扩的量与软边宽度。 */
    private static final float GLOW_SPREAD = 2f;
    private static final float GLOW_FEATHER = 3f;

    /**
     * 经验卡的图标：下界之星。
     * <p>
     * 【为什么是它】经验没有 ItemStack，原先画一块强调色方块占位。但"是什么"这件事
     * 不能靠形状猜 —— 卡片上得有个一眼认得出的记号。设计稿一直用的是下界之星贴图，
     * 参考图里经验卡那一格的主导色也正好是 nether_star.png 的三个主色
     * （#556B6B / #88A4A4 / #B9C9C9），所以照它来。
     * <p>
     * 只用来画，不动它，所以是共享的一份 —— 渲染路径不会改栈。
     */
    private static final ItemStack XP_ICON = new ItemStack(Items.NETHER_STAR);

    /** pose 变换用的复用向量，避免逐顶点分配。 */
    private static final Vector3f POS = new Vector3f();

    /** "引擎没了"这件事每次会话只该在屏幕上说一次，日志里也只需要一条。 */
    private boolean reportedMissing;

    // ------------------------------------------------------------------
    // 一帧：HUD 与 dev harness 共用这一条路径
    // ------------------------------------------------------------------

    /**
     * 画这一帧的所有卡。
     *
     * <p>【为什么先 gui.flush()】NanoVG 是直接 GL：前面 HUD 打出来的原版批次还排在
     * {@code bufferSource} 里，不等它们上 GPU 就开 NanoVG 的帧，两边的 GL 状态会打架
     * （症状是卡片周围的人名/提示错位或消失）。
     */
    public void paint(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots) {
        Font font = Minecraft.getInstance().font;

        gui.flush();

        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg == null || !nvg.valid()) {
            if (!reportedMissing) {
                reportedMissing = true;
                PickupCard.LOGGER.error("NanoVG 上下文不可用：卡片这一帧不画。"
                        + "日志上面那条 [nvg] 有原因；native 缺失是发布打包的问题（见 build.gradle 的 unpackNvg）。");
            }
            return;
        }

        StyleModel style = canvas.style();
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
        try {
            long vg = nvg.handle();
            for (CardSlot slot : slots) {
                float rise = canvas.contentOf(slot.view());
                Inbox.Card card = slot.view().notice().payload();
                if (slot.view().exiting() || slot.view().reviving()) {
                    // 【为什么要这一行】用户报过「淡出最后一帧图标和文字完全不透明，然后消失」。
                    // 这件事只有逐帧数值能定死：alpha 一路单调到 0 说明问题在绘制那一路；
                    // alpha 中途跳回 1 就是这张卡被救回来 / 重挂了（见 CardView#absorbMerge）。
                    // 【退场诊断降为 debug】病根修掉后它只剩复查价值——挂 debug 级且只在
                    // debug 开启时才拼字符串，退场期一帧两次格式化不再进热路径（2026-09-20 性能轮）。
                    if (PickupCard.LOGGER.isDebugEnabled()) {
                        PickupCard.LOGGER.debug("[退场/淡回] key={} 进度={} alpha={}", slot.view().key(),
                                String.format(java.util.Locale.ROOT, "%.2f",
                                        slot.view().reviving() ? canvas.reviveOf(slot.view())
                                                : canvas.exitOf(slot.view())),
                                String.format(java.util.Locale.ROOT, "%.2f", exitAlphaOf(canvas, slot)));
                    }
                }
                nvgSave(vg);
                // 退场：整张卡的外壳（竖条 + 两个框 + 微光）一起淡，见类注释。
                // 【图标不在这里淡】图标每帧原版现渲（FadingItemBuffers），它的 alpha 走
                // setShaderColor —— 三条通道同一个数（exitAlphaOf），API 各归各。
                nvgGlobalAlpha(vg, exitAlphaOf(canvas, slot));
                // 【缩放落在变换上】外壳一律按"未缩放的卡"画，位置与大小由这两个变换给。
                // 这样竖条宽、圆角、描边、微光全都一起缩，不会出现"卡小了但边还是粗的"。
                // NanoVG 的 scissor 也会被当前变换带走，所以 paintShell 里的裁剪框照样对。
                // 【两个缩放不是一回事，别合并】S 是布局缩放（把"未缩放的卡"换算成屏幕像素），
                // p 是脉冲倍率（再次拾起时整张卡鼓一下）。外壳按 W0×H0 画，屏幕尺寸 = W0·S·p，
                // 所以 W0 要除 S 而不是除 S·p —— 合并成一个的话卡会越鼓越小。
                // 【脉冲为什么以卡心为原点】以左上角为原点时卡片会一边放大一边往右下"长出去"，
                // 读起来是位移；以卡心为原点才是原地鼓。内容那一路必须用同一个原点，否则错开。
                float cardScale = canvas.scale();
                float pulse = canvas.pulseOf(slot.view());
                float w0 = slot.width() / cardScale;
                float h0 = slot.height() / cardScale;
                nvgTranslate(vg, slot.x() + slot.width() / 2f, slot.y() + slot.height() / 2f);
                nvgScale(vg, cardScale * pulse, cardScale * pulse);
                paintShell(vg, style, -w0 / 2f, -h0 / 2f, w0, h0,
                        accentOf(card, style.accents()), canvas.barOf(slot.view()),
                        bodyShiftOf(canvas, slot, style, rise), rise, glowStrengthOf(card),
                        glowScaleOf(canvas, slot), shimmerOf(canvas, slot),
                        windowOf(canvas, slot, style, rise));
                nvgRestore(vg);
            }
        } finally {
            nvg.end();
        }

        // 内容排在 NanoVG 之后：它画在卡面之上（用的是同一批屏幕坐标）。
        // 图标与文字都在这一路 —— 图标每帧原版现渲（见 content 里的说明），文字走原版字形。
        for (CardSlot slot : slots) {
            content(gui, canvas, slot, font);
        }
    }

    /** 这张卡该画的图标栈：普通物品 / 溢出卡的轮换图标 / 经验卡的固定替代。 */
    private static ItemStack iconStackOf(CardCanvas canvas, CardSlot slot) {
        Inbox.Card card = slot.view().notice().payload();
        if (card.content() instanceof CardContent.Item item) {
            return item.stack();
        }
        if (card.content() instanceof CardContent.Overflow overflow) {
            return cycleIcon(overflow, canvas.now());
        }
        return XP_ICON;
    }

    // ------------------------------------------------------------------
    // 外壳：影子 -> 竖条 -> 两个框 -> 微光
    // ------------------------------------------------------------------

    /**
     * 一张卡的全部矢量部分。退场淡出不在这里 —— 调用方用 {@code nvgGlobalAlpha} 一笔带过，
     * 这里只管"浓度随入场进度"的那部分（影子、竖条）。
     *
     * @param x,y      卡片左缘 / 顶边（屏幕逻辑坐标）
     * @param barFill  竖条展开比例 0~1
     * @param bodyShift 两个内容框的横向偏移（内容从竖条后面滑出来用）；竖条自己不动，
     *                  它是"洞口"，所以只有内容偏移。退场「火车退回」的位移也从这里进。
     * @param rise     内容出现进度 0~1。影子浓度与微光都按它给 —— 用户报过"影子一出来就是
     *                 满的，看着像影子先到、卡片后到"
     * @param glowStrength 微光强度 0~1（{@link #glowStrengthOf} 给：按稀有度档位爬阶梯）。
     *                     0 = 这张卡没有微光
     * @param glowScale 微光呼吸系数（0.4~1.0，关闭呼吸恒为 1）
     * @param shimmer  入场扫光进度：0 = 不画；(0,1) = 光带走到哪（{@link #shimmerOf} 给）
     * @param window   隧道口：内容能被看见的那一段。**偏移必须有它配套**：只有偏移没有裁剪，
     *                 两个框就会从竖条前面滑过去 —— 真机上看就是"卡片穿透竖条"。
     *                 消失方式「拉幕收拢」也从这里进：退场时窗口从右往左收。
     */
    public static void paintShell(long vg, StyleModel style, float x, float y, float cardW, float cardH,
                                  int accent, float barFill, float bodyShift, float rise,
                                  float glowStrength, float glowScale, float shimmer, RevealWindow window) {
        float gap = style.gap();
        float barW = style.barWidth();
        float bodyX = barW + gap;
        float bodyW = Math.max(0f, cardW - bodyX);
        float radius = Math.min(style.cornerRadius(), cardH / 2f);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 竖条在窗口左边，是"洞口"，不受窗口影响，单独画
            bar(vg, stack, style, x, y, cardH, radius, accent, barFill);

            // 【两个内容框必须在窗口里画】偏移让框往左走，窗口把左边多出来的那截切掉，
            // 合起来才是"从隧道口冒出来"。用 save/restore 包住：scissor 是 NanoVG 的
            // 状态，留着会影响后面每一张卡。
            nvgSave(vg);
            nvgScissor(vg, x + window.left(), y, window.width(), cardH);
            if (window.width() > 0.01f) {
                box(vg, stack, style, x + bodyX + bodyShift, y, cardH, cardH, radius);
                float infoX = x + bodyX + cardH + gap;
                float infoW = Math.max(0f, x + cardW - infoX);
                if (infoW > 0f) {
                    box(vg, stack, style, infoX + bodyShift, y, infoW, cardH, radius);
                }
                // 扫光在框之后、微光之前：它要照亮的是框面（在框上才读得出"掠过"），
                // 又必须被窗口裁着（入场未完成时不许越出洞口）
                if (shimmer > 0f) {
                    shimmerBand(vg, stack, style, x + bodyX, y, bodyW, cardH, radius, shimmer);
                }
            }
            nvgRestore(vg);

            // 微光叠在外壳之"上"：画在框之前会被底色盖掉，看起来就是没画。
            // glowScale 是呼吸系数（0.4~1.0，关闭呼吸时恒 1）—— 见 glowScaleOf。
            // glowStrength 是档位阶梯（见 glowStrengthOf）：强度 = 基础 alpha × 档位 × 呼吸。
            if (glowStrength > 0f && rise > 0.5f && style.glowAlpha() > 0) {
                softBox(vg, stack, x + bodyX - GLOW_SPREAD, y - GLOW_SPREAD,
                        bodyW + GLOW_SPREAD * 2f, cardH + GLOW_SPREAD * 2f, radius + GLOW_SPREAD,
                        GLOW_FEATHER, withAlpha(accent,
                                Math.round(style.glowAlpha() * glowScale * glowStrength)));
            }
        }
    }

    /**
     * 扫光带：一道竖向光带从内容框左缘走到右缘，亮度按 sin(π·p) 起落（起=淡入，落=淡出）。
     * <p>【为什么是"彗星"而不是对称光带】线性渐变只有两个停靠点：透明→亮。移动起来亮头
     * 在前、淡尾在后，读起来就是一颗掠过的光；为了对称再叠第二条渐变，多一次填充也换不回
     * 能看见的差别。颜色用白而不是强调色：强调色已经在竖条和微光上说话了，扫光说"有光
     * 掠过"这件事，白色最不带歧义。
     */
    private static void shimmerBand(long vg, MemoryStack stack, StyleModel style,
                                    float x, float y, float w, float h, float radius, float p) {
        float bandW = Math.max(24f, h * 1.2f);
        // 出发时整条带在框左缘外，收工时整条带在右缘外：行程要含两条带宽，否则最后 1/4
        // 的淡出发生在画面外，观众看到的扫光是"突然没了一半"
        float bandX = x - bandW + (w + bandW * 2f) * p;
        float envelope = (float) Math.sin(Math.PI * p);
        int peak = Math.round(style.shimmerAlpha() * envelope);
        NVGPaint paint = nvgLinearGradient(vg, bandX, y, bandX + bandW, y,
                color(stack, 0x00FFFFFF), color(stack, withAlpha(0xFFFFFFFF, peak)),
                NVGPaint.mallocStack(stack));
        nvgBeginPath(vg);
        nvgRoundedRect(vg, bandX, y, bandW, h, Math.min(radius, h / 4f));
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    /**
     * 一个框：渐变底 -> 内缩 1px 描边（顺序与 CSS 叠法一致）。
     * <p>
     * 【填充与描边共用同一条路径 —— border-box】NanoVG 的 stroke 骑在路径上（各出一半），
     * 路径内缩半个线宽后，描边外缘正好落在矩形原边上。圆角半径必须<b>同步减</b>半个线宽：
     * 从前只内缩矩形、半径照旧，四个角上描边外缘比填充的圆角缩进去零点几个像素，
     * 暗色底就从描边外面露出来一圈（用户报的「背景溢出边框一点点」就是它）。
     * 填充沿同一条路径画，被描边盖住的那半圈正好是接缝 —— 既不露底也不开缝。
     */
    private static void box(long vg, MemoryStack stack, StyleModel style,
                            float x, float y, float w, float h, float radius) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        float stroke = style.borderWidth();
        float half = stroke / 2f;
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x + half, y + half, Math.max(0f, w - stroke), Math.max(0f, h - stroke),
                Math.max(0f, radius - half));
        NVGPaint paint = nvgLinearGradient(vg, x + half, y + half, x + half, y + half + Math.max(0f, h - stroke),
                color(stack, style.fillTop()), color(stack, style.fillBottom()), NVGPaint.mallocStack(stack));
        nvgFillPaint(vg, paint);
        nvgFill(vg);

        if (stroke > 0f) {
            nvgStrokeWidth(vg, stroke);
            nvgStrokeColor(vg, color(stack, style.border()));
            nvgStroke(vg);
        }
    }

    /** 稀有度竖条：从上往下长，不是从中间往两头长（用户报过那个版本）。 */
    private static void bar(long vg, MemoryStack stack, StyleModel style, float x, float y, float cardH,
                            float radius, int accent, float barFill) {
        float inset = style.barInsetY();
        float full = Math.max(0f, cardH - inset * 2f);
        float barH = full * Easing.clamp01(barFill);
        if (barH <= 0.01f) {
            return;
        }
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y + inset, style.barWidth(), barH,
                Math.min(radius, style.barWidth() / 2f));
        nvgFillColor(vg, color(stack, accent));
        nvgFill(vg);
    }

    // ------------------------------------------------------------------
    // 微光的软边：NanoVG 里没有模糊，用 boxGradient 做
    // ------------------------------------------------------------------

    /**
     * 一块<b>软边矩形</b>：矩形的边往外 {@code feather} 像素由 {@code argb} 渐变到全透明，
     * 矩形内部是实心 {@code argb}。
     *
     * <p>【现在只有微光用它】投影删了之后，这是唯一还需要软边的地方（{@link #softBox} 的
     * 名字是照着当年的用途留下的）。NanoVG 没有高斯模糊，但 {@code nvgBoxGradient} 干的
     * 正好是这件事：一个从"框内实心"到"框外 N 像素处透明"的渐变。
     */
    private static void softBox(long vg, MemoryStack stack, float x, float y, float w, float h,
                                float radius, float feather, int argb) {
        int alpha = argb >>> 24;
        if (w <= 0f || h <= 0f || feather <= 0f || alpha == 0) {
            return;
        }
        NVGColor inner = color(stack, argb);
        NVGColor outer = color(stack, argb & 0x00FFFFFF);
        NVGPaint paint = nvgBoxGradient(vg, x, y, w, h, radius, feather, inner, outer,
                NVGPaint.mallocStack(stack));
        nvgBeginPath(vg);
        // 路径要比框大一圈：外圈的渐变也在这条路径里面才是画得出来的
        nvgRect(vg, x - feather, y - feather, w + feather * 2f, h + feather * 2f);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    // ------------------------------------------------------------------
    // 内容：物品图标 + 名字 + 数量（原版批次，画在卡面之上）
    // ------------------------------------------------------------------

    /** 只画原版那部分，带入场裁剪。 */
    private static void content(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        StyleModel style = canvas.style();
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();
        // 【内容全部在"未缩放单位"里算】缩放交给 pose；文字因此跟着一起缩，
        // 而字形按 100% 栅格化后重采样 —— 缩小时略有锯齿，但比"卡小了字还在外面"强。
        // 尺寸按布局缩放换算（见 paint 里那段：S 与脉冲 p 是两回事）
        float cardScale = canvas.scale();
        float h = slot.height() / cardScale;
        float cardW = slot.width() / cardScale;
        float gap = style.gap();
        float barW = style.barWidth();
        float bodyX = barW + gap;
        float rise = canvas.contentOf(view);
        RevealWindow win = windowOf(canvas, slot, style, rise);
        float shift = bodyShiftOf(canvas, slot, style, rise);
        float alpha = exitAlphaOf(canvas, slot);
        float x = bodyX + shift;
        int accent = accentOf(card, style.accents());

        gui.pose().pushPose();
        // 与外壳同一个变换：以卡心为原点、按 S·p 缩放（脉冲内外一致，见 paint 里那段说明）
        float effScale = cardScale * canvas.pulseOf(view);
        gui.pose().translate(slot.x() + slot.width() / 2f, slot.y() + slot.height() / 2f, 0f);
        if (effScale != 1f) {
            gui.pose().scale(effScale, effScale, 1f);
        }
        gui.pose().translate(-cardW / 2f, -h / 2f, 0f);
        // 裁剪在两种情况下都要在：入场还没走完（隧道口在开），或者退场选了带位移/收拢的类型
        //（淡出不需要——它没有几何变化，裁着白费）。
        boolean clipped = rise < 1f
                || (view.exiting() && canvas.layout().exitMode() != LayoutSettings.Exit.FADE);
        if (clipped) {
            scissor(gui, gui.pose(), win, h);
        }

        // 【图标：每帧原版现渲 + 退场换层淡出（2026-09-19 治本定案）】烘焙快照路线
        // （ItemIconCache，本版已删）拿到过真 alpha 淡出，但快照冻住了活的东西：附魔光
        // 不再滚动（动效丢失）、分辨率钉死在离屏贴图（锯齿）、glint 亮条纹烘丢（变暗）、
        // 读回行翻转账（颠倒）。根治 = 不再快照：图标永远原版 renderItem，只在退场
        // alpha<1 的帧里由 FadingItemBuffers 把 NO_BLEND 实体层换到开混合的等价层提交 ——
        // setShaderColor 的 alpha 从此数学上有效。几何（窗口/位移/缩放/裁剪）与外壳同源，
        // 三档退场都跟着卡走。
        ItemStack iconStack = iconStackOf(canvas, slot);
        if (!iconStack.isEmpty()) {
            FadingItemBuffers.drawIcon(gui, iconStack, x + h / 2f, h / 2f, style.iconSize(), alpha,
                    clipped);
        }

        // 文字：alpha 直接乘进颜色里（原版字形用的就是这个色的 alpha），不走全局色。
        // 【alpha 字节掉到 4 以下就整段不画】原版 Font.adjustColor（1.20.1 Font.java:109）
        // 会把 alpha 字节 0~3 的颜色强制改成完全 opaque —— 退场末尾 alpha 单调下穿这个区间，
        // 那几帧文字会「闪回不透明」，一帧后整卡才被摘掉（用户连报两次的末帧闪就是它）。
        String count = canvas.countText(view.notice().count());
        float textY = (h - font.lineHeight) / 2f;
        if (canvas.settings().showItemName() && textVisible(style.nameColor(), alpha)) {
            String name = CardMetrics.fittedName(canvas, font, card, view.notice().count());
            float nameX = x + h + gap;
            gui.drawString(font, name, Math.round(nameX + style.paddingH()), Math.round(textY),
                    fade(style.nameColor(), alpha), true);
        }
        drawCount(gui, canvas, view, font, cardW + shift - style.paddingH(), textY, accent, alpha);

        if (clipped) {
            // 原版内容还在 bufferSource 里排队：不在这里冲掉，它会在裁剪失效之后才画出来
            gui.bufferSource().endBatch();
            gui.disableScissor();
        }
        gui.pose().popPose();
    }

    // ------------------------------------------------------------------
    // 配置界面的实时预览
    // ------------------------------------------------------------------

    // 【这里原来有一个 paintPreview：单独把静止的最终态重画一遍】
    // 它 2026-09-18 删掉了，原因有两层：
    //   ① 它和 drawContent 是**两份**绘制实现（图标 / 名字截断 / 数字各画一遍），
    //      而预览迟早和真卡不一样就是这个界面的老毛病；
    //   ② 用户要预览**重播时间线**（第 2 条），而那份静止实现没有时间的概念。
    // 现在配置界面自己造一张真的 Notice + CardView + CardSlot，直接调本类的 paint() ——
    // 预览与游戏里共用同一条时间线、同一份排版、同一套截断，改了真卡预览自动跟上。
    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /** 这一帧这张卡的隧道口：入场展开 + 消失收拢都从它进（两种方式只差宽度与方向）。 */
    private static RevealWindow windowOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        // 窗口是"卡内坐标"，所以要用未缩放的宽度（它在变换后的空间里被解释）
        float w0 = slot.width() / canvas.scale();
        RevealWindow win = RevealWindow.of(style.barWidth(), style.gap(), w0,
                canvas.layout().appearMode() == LayoutSettings.Appear.CLIP, rise);
        // 【消失方式＝拉幕收拢】可见范围从右往左收窄：拉幕入场的逆放。与入场窗口取 min ——
        // 万一"还没展开完就开始退"也不会越宽。
        if (canvas.layout().exitMode() == LayoutSettings.Exit.WIPE && slot.view().exiting()) {
            float full = RevealWindow.contentWidth(w0, style.barWidth(), style.gap());
            win = new RevealWindow(win.left(), Math.min(win.width(),
                    full * (1f - Easing.clamp01(canvas.exitOf(slot.view())))));
        }
        return win;
    }

    /** 内容横向滑动量：CLIP 是"窗口变宽、内容不动"，另一模式是内容从竖条后面平移出来。 */
    private static float bodyShiftOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        float bodyW = Math.max(0f, slot.width() / canvas.scale() - style.barWidth() - style.gap());
        float shift = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP
                ? 0f : -(1f - rise) * bodyW;
        // 【消失方式＝火车退回】内容整块平移回竖条后面：火车入场的逆放。窗口把左边裁住，
        // 视觉就是"倒车回隧道"。
        if (canvas.layout().exitMode() == LayoutSettings.Exit.TRAIN && slot.view().exiting()) {
            shift -= canvas.exitOf(slot.view()) * bodyW;
        }
        return shift;
    }

    /**
     * 微光这一帧的亮度系数（0.4~1.0）：呼吸往复，透明度按正弦摆动；
     * 每张卡用 key 散列错开相位，一摞卡不会齐步闪烁。主题 {@code glowPulseEnabled}
     * 关掉即恒定 1.0（它从前是个没有任何代码读的死参数）。
     */
    private static float glowScaleOf(CardCanvas canvas, CardSlot slot) {
        if (!canvas.style().glowPulseEnabled()) {
            return 1f;
        }
        float phase = (slot.view().key().hashCode() & 0xFFFF) / 65536f;
        double wave = Math.sin((canvas.now() % 100_000L) / 1600.0 * 2.0 * Math.PI
                + phase * 2.0 * Math.PI);
        return 0.7f + 0.3f * (float) wave;
    }

    /**
     * 退场不透明度（1 → 0）。入场一律是 1 —— 草稿里新卡是"原地出现"的，不淡入。
     * <p>
     * 【为什么退场<b>没有</b>位移】草稿里退役那张确实"升一格 + 淡出"，但那"一格"不是画上去
     * 的：它还在 live 里，布局会把它排到堆顶<b>上面</b>那一行（见 {@code StackLayout}），
     * {@code CardMove} 再用 340ms 把它推上去。这里要是再加一段位移，它就要动两格了。
     * 曲线取 easeOutCubic：快出慢停，{@code Easing} 里本来就注着"透明度的默认选择"。
     */
    private static float exitAlphaOf(CardCanvas canvas, CardSlot slot) {
        return canvas.exitAlphaOf(slot.view());
    }

    private static int accentOf(Inbox.Card card, StyleModel.Accents accents) {
        // 【为什么用 if 而不是 switch】1.20.1 这一支是 Java 17，模式匹配的 switch 还是预览特性
        if (card.content() instanceof CardContent.Item item) {
            return RarityAccent.of(item.stack(), accents);
        }
        return card.content() instanceof CardContent.Overflow
                ? RarityAccent.overflow(accents) : RarityAccent.xp(accents);
    }

    /**
     * 溢出卡的图标轮播：在成员之间轮流显示。
     * <p>
     * 【间隔为什么随张数变慢】3 个图标时快点没问题，8 个时再快就成了闪烁。常量是我们自己定的
     * （下限 1/4 秒、每多一个成员再慢 40ms），只借"成员越多、轮得越慢"这个行为。
     */
    private static ItemStack cycleIcon(CardContent.Overflow overflow, long nowMs) {
        List<ItemStack> stacks = overflow.stacks();
        if (stacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long interval = Math.max(250L, 900L - 40L * stacks.size());
        return stacks.get((int) ((nowMs / interval) % stacks.size()));
    }

    /**
     * 这张卡的微光强度（0~1）：微光从「有 / 无」改成按稀有度档位爬阶梯（2026-09-20 用户选型）。
     * <p>经验卡与白名单强调卡维持满强度——那是玩家已经看惯的参考观感，不为阶梯让路；
     * 物品卡从 rare(3) 起步、每升一档亮一截。阶梯落在统一档位尺（{@link RarityAccent#tierOf}）
     * 上，vanilla 的 epic(4) 与 RC 的 legendary(5) 自然各就各位。
     */
    private static float glowStrengthOf(Inbox.Card card) {
        if (card.content() instanceof CardContent.Experience || card.emphasized()) {
            return 1f;
        }
        if (card.content() instanceof CardContent.Item item) {
            int tier = RarityAccent.tierOf(item.stack());
            return tier >= 3 ? Math.min(1f, 0.3f + 0.15f * (tier - 2)) : 0f;
        }
        return 0f;
    }

    /**
     * 入场扫光进度：0 = 不画；(0,1) = 光带走到了哪。
     * <p>【为什么窗口接在入场后面】入场是"内容滑出来"，扫光是"光从停稳的卡上掠过去"——
     * 同时跑互相抢戏；入场关掉（瞬间出现）就从落座那一刻起跑。
     * <p>【为什么是一次性的】循环扫光的高稀有卡会永远在喊"看我"，一摞上去就是迪厅；
     * 一次性 + 只给高稀有档（{@link RarityAccent#showcaseFrom}，vanilla rare+ / RC 5+）
     * = 拾取瞬间的奖励感，不是常驻装饰。
     */
    private static float shimmerOf(CardCanvas canvas, CardSlot slot) {
        StyleModel style = canvas.style();
        if (style.shimmerAlpha() <= 0) {
            return 0f;
        }
        Inbox.Card card = slot.view().notice().payload();
        if (!(card.content() instanceof CardContent.Item item)) {
            return 0f;
        }
        if (RarityAccent.tierOf(item.stack()) < RarityAccent.showcaseFrom()) {
            return 0f;
        }
        long start = slot.view().notice().bornAt()
                + (style.enterEnabled() ? style.enterMs() : 0L);
        float p = (canvas.now() - start) / 480f;
        return p <= 0f || p >= 1f ? 0f : p;
    }

    /**
     * 把局部坐标的裁剪窗按当前 pose 变换后交给 scissor。
     * <p>
     * 入场只有平移与缩放、没有旋转，所以四角包围盒是精确的。不这么做的话，动起来的那几帧
     * 裁剪框会停在原地，卡片"从框里滑出去"。
     */
    private static void scissor(GuiGraphics gui, com.mojang.blaze3d.vertex.PoseStack pose,
                                RevealWindow win, float h) {
        Matrix4f m = pose.last().pose();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            // 【左边是窗口的左边，不是卡片左缘】写 0 的话，内容在竖条那一段也照画不误
            POS.set(i % 2 == 0 ? win.left() : win.right(), i < 2 ? 0f : h, 0f);
            m.transformPosition(POS);
            minX = Math.min(minX, POS.x);
            minY = Math.min(minY, POS.y);
            maxX = Math.max(maxX, POS.x);
            maxY = Math.max(maxY, POS.y);
        }
        gui.enableScissor((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }

    /**
     * 数量：平时就是一行字；合并那一刻<b>从旧值滚到新值</b>（用户 2026-09-17 选的 C 档）。
     * <p>【为什么要把两行字裁在一个框里】滚动是"旧的往上走、新的从下面上来"。不裁剪的话
     * 两行会同时完整地叠在那儿，看着像重影；裁在数字这一行的高度里，才是"卷上去"。
     * <p>【为什么整串滚，而不是逐位滚】逐位要在等宽数字上做进位对齐，而位数变化（9 → 10）
     * 根本没有对应关系。整串滚在位数变化时一样成立，读起来也不差。
     */
    private static void drawCount(GuiGraphics gui, CardCanvas canvas, CardView view, Font font,
                                  float right, float textY, int accent, float alpha) {
        // 同「末帧闪」的闸：数量与名字走同一个原版 drawString，同一个坑
        if (!textVisible(accent, alpha)) {
            return;
        }
        String cur = canvas.countText(view.notice().count());
        String prev = canvas.prevCountText(view);
        float roll = canvas.rollOf(view);
        if (prev == null || roll >= 1f) {
            gui.drawString(font, cur, Math.round(right - font.width(cur)),
                    Math.round(textY), fade(accent, alpha), true);
            return;
        }
        float t = Easing.easeOutCubic(roll);
        float lineH = font.lineHeight;
        float left = right - Math.max(font.width(cur), font.width(prev));
        // 【为什么先 flush】裁剪是"画的时候才生效"的，而前面几张卡的文字正排着队还没提交 ——
        // 不冲掉的话它们会一起被这个框裁掉（同一批 buffer 共用同一个裁剪状态）。
        gui.flush();
        // 【坐标必须是屏幕坐标】这里的 left/right/textY 是"卡内未缩放单位"，而
        // GuiGraphics#enableScissor 吃的是经当前 pose 变换后的屏幕像素 —— 直接把卡内坐标喂进去
        // 会得到一个贴着画布左上角的小框，等于把这一行字整个裁没（2026-09-18 就这么错过一次：
        // 合并之后数字从屏幕上彻底消失）。所以跟 scissor() 一样先过一遍矩阵。
        scissorLocal(gui, gui.pose(), left, textY, right, textY + lineH);
        gui.drawString(font, prev, Math.round(right - font.width(prev)),
                Math.round(textY - t * lineH), fade(accent, alpha), true);
        gui.drawString(font, cur, Math.round(right - font.width(cur)),
                Math.round(textY + (1f - t) * lineH), fade(accent, alpha), true);
        gui.flush();
        gui.disableScissor();
    }

    /**
     * 把一个<b>卡内未缩放单位</b>的矩形变成屏幕像素并开启裁剪。
     * <p>与 {@link #scissor} 同一件事，区别只是它吃的是 {@code RevealWindow}、这里吃四个边。
     */
    private static void scissorLocal(GuiGraphics gui, PoseStack pose,
                                     float x1, float y1, float x2, float y2) {
        Matrix4f m = pose.last().pose();
        Vector3f a = m.transformPosition(new Vector3f(x1, y1, 0f));
        Vector3f b = m.transformPosition(new Vector3f(x2, y2, 0f));
        gui.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
    }

    /** 把颜色自带的 alpha 再乘一个系数：{@code fade(0x80FF0000, 0.5f)} → alpha 64。 */
    private static int fade(int argb, float alpha) {
        if (alpha >= 0.999f) {
            return argb;
        }
        return withAlpha(argb, Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(alpha)));
    }

    /**
     * 这一档不透明度还<b>该不该画字</b>：算出来的 alpha 字节一旦小于 4，原版
     * {@code Font.adjustColor} 会把它强制改成完全不透明（见 {@link #content} 里的说明），
     * 所以不是「画得淡」而是「干脆不画」—— 宁可让文字比外壳早没半帧，也不闪那一下。
     */
    private static boolean textVisible(int argb, float alpha) {
        if (alpha >= 0.999f) {
            return true;
        }
        return Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(alpha)) >= 4;
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.min(255, Math.max(0, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** 主题里的 ARGB -> NanoVG 要的 RGBA 分量。 */
    private static NVGColor color(MemoryStack stack, int argb) {
        return nvgRGBA((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24),
                NVGColor.mallocStack(stack));
    }
}
