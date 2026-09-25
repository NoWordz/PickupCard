package com.niuqu.pickupcard.render.nvg;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.BatchStats;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.render.FadingItemBuffers;
import com.niuqu.pickupcard.style.BodyGeometry;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.RevealWindow;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 卡面的<b>内容路</b>：物品图标（原版现渲 + 退场换层）与名字/数量（原版字形）。
 * <p>
 * 【为什么和外壳分成两个类】一条卡面有两套渲染管线：外壳是 NanoVG 直接 GL（见
 * {@link NvgCardPainter#paintShell}），内容走原版批次（图标是 {@code ItemRenderer} 的
 * 现渲结果、文字是字形图集）。两者共享时间线取值（窗口/位移/退场 alpha，从
 * {@code NvgCardPainter} 的包可见助手拿），但绘制方式、状态管理、坑位完全不同——
 * 挤在一个类里，736 行里有 400 行是另一个世界。2026-09-20 审计后拆出，行为零变化。
 * <p>
 * 【三通道一个数】外壳淡出走 {@code nvgGlobalAlpha}、图标走换层后的
 * {@code setShaderColor}、文字走颜色 alpha——三条通道必须是同一个数（退场 alpha，
 * 从 {@code exitAlphaOf} 拿），这里负责后两条。
 */
public final class NvgCardContent {

    /** 经验卡的图标：下界之星。
     * <p>
     * 【为什么是它】经验没有 ItemStack，原先画一块强调色方块占位。但"是什么"这件事
     * 不能靠形状猜 —— 卡片上得有个一眼认得出的记号。设计稿一直用的是下界之星贴图，
     * 参考图里经验卡那一格的主导色也正好是 nether_star.png 的三个主色
     * （#556B6B / #88A4A4 / #B9C9C9），所以照它来。
     * <p>只用来画，不动它，所以是共享的一份 —— 渲染路径不会改栈。
     */
    private static final ItemStack XP_ICON = new ItemStack(Items.NETHER_STAR);

    /** scissorLocal 的复用向量，避免逐帧分配（见该方法）。 */
    private static final Vector3f CORNER_A = new Vector3f();
    private static final Vector3f CORNER_B = new Vector3f();

    /** 分段计时（-Dpickupcard.profile=1 时逐帧打印）：图标与文字各花了多久。 */
    static long profileIconUs;
    static long profileTextUs;

    private NvgCardContent() {
    }

    /** 画一张卡的原版内容：图标 + 名字 + 数量，带入场/退场裁剪。 */
    public static void paint(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        StyleModel style = canvas.style();
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();
        // 【内容全部在"未缩放单位"里算】缩放交给 pose；文字因此跟着一起缩，
        // 而字形按 100% 栅格化后重采样 —— 缩小时略有锯齿，但比"卡小了字还在外面"强。
        // 尺寸按布局缩放换算（见 NvgCardPainter.paint 里那段：S 与脉冲 p 是两回事）
        float cardScale = canvas.scale();
        float h = slot.height() / cardScale;
        float cardW = slot.width() / cardScale;
        float gap = style.gap();
        boolean mirror = canvas.layout().mirrorCard();
        // 三个框的自然位置与外壳<b>共用一份几何</b>（BodyGeometry）：从前两边各推一遍，
        // 镜像时这里把滑出方向算反了 —— 外壳从竖条（右）那侧滑出、文字与图标却从左边冒出来，
        // 稳态看不出来、只有动画途中现形（用户 2026-09-20 报「卡片动画镜像了、文字动画没有」）。
        BodyGeometry body = BodyGeometry.of(cardW, h, style.barWidth(), gap, mirror);
        float rise = canvas.contentOf(view);
        RevealWindow win = NvgCardPainter.windowOf(canvas, slot, style, rise);
        float shift = NvgCardPainter.bodyShiftOf(canvas, slot, style, rise);
        float alpha = NvgCardPainter.exitAlphaOf(canvas, slot);
        int accent = NvgCardPainter.accentOf(card, style.accents());

        gui.pose().pushPose();
        // 与外壳同一个变换：以卡心为原点、按 S·p·exitScale 缩放（脉冲内外一致）。
        // 【退场缩放与外壳同吃一个数】SCALE 档的收缩因子由 NvgCardPainter#cardScaleOf
        // 一处给出（乘法关系，不新增变换栈）—— 2026-09-20 镜像 bug 的教训。
        float effScale = cardScale * canvas.pulseOf(view) * NvgCardPainter.cardScaleOf(canvas, slot);
        // 【纵向位移与外壳同吃一个数】DROP 入场 / FALL 退场的竖向位移由
        // NvgCardPainter#verticalShiftOf 一处给出（2026-09-20 镜像 bug 的教训：
        // 两份实现必有一份错）—— 外壳的 nvgTranslate 加的就是同一个函数的返回值。
        gui.pose().translate(slot.x() + slot.width() / 2f,
                slot.y() + slot.height() / 2f + NvgCardPainter.verticalShiftOf(canvas, slot), 0f);
        if (effScale != 1f) {
            gui.pose().scale(effScale, effScale, 1f);
        }
        // 【sway 与外壳同吃一个数、同枢轴】角度由 swayAngleOf 一处给出；当前原点在卡心
        // （位移之后、回到左上角之前），与外壳 nvgRotate 的枢轴一致。sway 非 0 时
        // clipped 必为 false（入场完且非退场），旋转不会跟 scissor 打架。
        float sway = NvgCardPainter.swayAngleOf(canvas, slot);
        if (sway != 0f) {
            gui.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sway));
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
        // 图标格左缘 = 自然位置 + 位移。【位移一律加、不乘方向】方向因子已经在
        // bodyShiftOf 里（镜像取正、常规取负），这里再取反一次等于把动画翻回去 ——
        // 2026-09-20 用户报的「文字动画没镜像」就是这一处。
        float iconL = body.iconLeft() + shift;
        // 【文本度量走这张卡的备忘】布局路与这里每一帧各问一次同一个名字，而它在一张卡的一生里
        // 几乎不变 —— 从前每问一次都要新建 Component、重跑翻译模板、逐码点量宽。
        // 2026-09-20 性能轮实测：把物品名整条关掉，稳态文字段 470~849us → 172~294us。
        var text = view.text();
        text.update(canvas, font, view);
        ItemStack iconStack = iconStackOf(canvas, slot);
        if (!iconStack.isEmpty()) {
            long iconT0 = NvgCardPainter.profiling() ? System.nanoTime() : 0L;
            FadingItemBuffers.drawIcon(gui, iconStack, iconL + h / 2f, h / 2f, style.iconSize(), alpha,
                    clipped);
            if (NvgCardPainter.profiling()) {
                profileIconUs += (System.nanoTime() - iconT0) / 1_000L;
            }
        }

        // 文字：alpha 直接乘进颜色里（原版字形用的就是这个色的 alpha），不走全局色。
        // 【alpha 字节掉到 4 以下就整段不画】原版 Font.adjustColor（1.20.1 Font.java:109）
        // 会把 alpha 字节 0~3 的颜色强制改成完全 opaque —— 退场末尾 alpha 单调下穿这个区间，
        // 那几帧文字会「闪回不透明」，一帧后整卡才被摘掉（用户连报两次的末帧闪就是它）。
        long textT0 = NvgCardPainter.profiling() ? System.nanoTime() : 0L;
        float textY = (h - font.lineHeight) / 2f;
        if (canvas.settings().showItemName() && textVisible(style.nameColor(), alpha)) {
            String name = text.fitted();
            if (mirror) {
                // 镜像：名字贴着图标格左侧排（右对齐），数量在信息框左端
                float nameRight = iconL - gap - style.paddingH();
                gui.drawString(font, name, Math.round(nameRight - text.fittedWidth()),
                        Math.round(textY), fade(style.nameColor(), alpha), true);
            } else {
                // 常规：名字排在图标格右侧（+ 一份水平内边距）
                float nameX = iconL + h + gap;
                gui.drawString(font, name, Math.round(nameX + style.paddingH()), Math.round(textY),
                        fade(style.nameColor(), alpha), true);
            }
        }
        // 数量锚点：非镜像=信息框右缘（往左排），镜像=信息框左缘（往右排）
        float countAnchor = mirror ? style.paddingH() + shift : cardW + shift - style.paddingH();
        drawCount(gui, canvas, view, font, countAnchor, textY, accent, alpha, mirror);
        if (NvgCardPainter.profiling()) {
            profileTextUs += (System.nanoTime() - textT0) / 1_000L;
        }

        if (clipped) {
            // 原版内容还在 bufferSource 里排队：不在这里冲掉，它会在裁剪失效之后才画出来
            BatchStats.countFlush();
            gui.bufferSource().endBatch();
            gui.disableScissor();
        }
        gui.pose().popPose();
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

    /**
     * 溢出卡的图标轮播：在成员之间轮流显示。
     * <p>
     * 【间隔为什么随张数变慢】3 个图标时快点没问题，8 个时再快就成了闪烁。常量是我们自己定的
     * （下限 1/4 秒、每多一个成员再慢 40ms），只借"成员越多、轮得越慢"这个行为。
     */
    private static ItemStack cycleIcon(CardContent.Overflow overflow, long nowMs) {
        java.util.List<ItemStack> stacks = overflow.stacks();
        if (stacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long interval = Math.max(250L, 900L - 40L * stacks.size());
        return stacks.get((int) ((nowMs / interval) % stacks.size()));
    }

    /**
     * 数量：平时就是一行字；合并那一刻<b>从旧值滚到新值</b>（用户 2026-09-17 选的 C 档）。
     * <p>【为什么要把两行字裁在一个框里】滚动是"旧的往上走、新的从下面上来"。不裁剪的话
     * 两行会同时完整地叠在那儿，看着像重影；裁在数字这一行的高度里，才是"卷上去"。
     * <p>【为什么整串滚，而不是逐位滚】逐位要在等宽数字上做进位对齐，而位数变化（9 → 10）
     * 根本没有对应关系。整串滚在位数变化时一样成立，读起来也不差。
     * <p>【锚点两义】非镜像 anchor=数量右缘（字往左排，数量贴卡右缘）；镜像 anchor=数量
     * 左缘（字往右排，数量贴信息框左端）——滚动框与对齐都跟着翻。
     */
    private static void drawCount(GuiGraphics gui, CardCanvas canvas, CardView view, Font font,
                                  float anchor, float textY, int accent, float alpha, boolean mirror) {
        // 同「末帧闪」的闸：数量与名字走同一个原版 drawString，同一个坑
        if (!textVisible(accent, alpha)) {
            return;
        }
        var text = view.text();
        String cur = text.countText();
        String prev = text.prevText();
        float roll = canvas.rollOf(view);
        if (prev == null || roll >= 1f) {
            float x = mirror ? anchor : anchor - text.countTextWidth();
            gui.drawString(font, cur, Math.round(x),
                    Math.round(textY), fade(accent, alpha), true);
            return;
        }
        float t = Easing.easeOutCubic(roll);
        float lineH = font.lineHeight;
        float w = text.countWidth();
        float left = mirror ? anchor : anchor - w;
        float right = left + w;
        // 【为什么先 flush】裁剪是"画的时候才生效"的，而前面几张卡的文字正排着队还没提交 ——
        // 不冲掉的话它们会一起被这个框裁掉（同一批 buffer 共用同一个裁剪状态）。
        BatchStats.countFlush();
        gui.flush();
        // 【坐标必须是屏幕坐标】这里的 left/right/textY 是"卡内未缩放单位"，而
        // GuiGraphics#enableScissor 吃的是经当前 pose 变换后的屏幕像素 —— 直接把卡内坐标喂进去
        // 会得到一个贴着画布左上角的小框，等于把这一行字整个裁没（2026-09-18 就这么错过一次：
        // 合并之后数字从屏幕上彻底消失）。所以跟外壳 scissor() 一样先过一遍矩阵。
        scissorLocal(gui, gui.pose(), left, textY, right, textY + lineH);
        float prevX = mirror ? left : right - font.width(prev);
        float curX = mirror ? left : right - text.countTextWidth();
        gui.drawString(font, prev, Math.round(prevX),
                Math.round(textY - t * lineH), fade(accent, alpha), true);
        gui.drawString(font, cur, Math.round(curX),
                Math.round(textY + (1f - t) * lineH), fade(accent, alpha), true);
        BatchStats.countFlush();
        gui.flush();
        gui.disableScissor();
    }

    /** 把局部坐标矩形经当前 pose 变换成屏幕像素并开启裁剪（滚动框专用，四个边直给）。 */
    private static void scissorLocal(GuiGraphics gui, PoseStack pose,
                                     float x1, float y1, float x2, float y2) {
        Matrix4f m = pose.last().pose();
        CORNER_A.set(x1, y1, 0f);
        CORNER_B.set(x2, y2, 0f);
        m.transformPosition(CORNER_A);
        m.transformPosition(CORNER_B);
        gui.enableScissor((int) Math.floor(Math.min(CORNER_A.x, CORNER_B.x)),
                (int) Math.floor(Math.min(CORNER_A.y, CORNER_B.y)),
                (int) Math.ceil(Math.max(CORNER_A.x, CORNER_B.x)),
                (int) Math.ceil(Math.max(CORNER_A.y, CORNER_B.y)));
    }

    private static void scissor(GuiGraphics gui, PoseStack pose, RevealWindow win, float h) {
        Matrix4f m = pose.last().pose();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            // 【左边是窗口的左边，不是卡片左缘】写 0 的话，内容在竖条那一段也照画不误
            Vector3f v = new Vector3f(i % 2 == 0 ? win.left() : win.right(), i < 2 ? 0f : h, 0f);
            m.transformPosition(v);
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
        }
        gui.enableScissor((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }

    /** 把颜色自带的 alpha 再乘一个系数：{@code fade(0x80FF0000, 0.5f)} → alpha 64。 */
    private static int fade(int argb, float alpha) {
        if (alpha >= 0.999f) {
            return argb;
        }
        return (Math.min(255, Math.max(0, Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(alpha)))) << 24)
                | (argb & 0xFFFFFF);
    }

    /**
     * 这一档不透明度还<b>该不该画字</b>：算出来的 alpha 字节一旦小于 4，原版
     * {@code Font.adjustColor} 会把它强制改成完全不透明（见 paint 里的说明），
     * 所以不是「画得淡」而是「干脆不画」—— 宁可让文字比外壳早没半帧，也不闪那一下。
     */
    private static boolean textVisible(int argb, float alpha) {
        if (alpha >= 0.999f) {
            return true;
        }
        return Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(alpha)) >= 4;
    }
}
