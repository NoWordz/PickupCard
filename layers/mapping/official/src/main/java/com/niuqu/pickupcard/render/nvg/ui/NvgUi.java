package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.render.nvg.NvgCanvas;
import com.niuqu.pickupcard.style.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;

/**
 * 自绘界面的一帧：<b>形状走 NanoVG，文字走原版字形</b>，一个 {@code AutoCloseable} 收尾。
 *
 * <p>【为什么要包一层】NanoVG 直接调 GL，而原版文字走的是延迟批次：
 * <ul>
 *   <li>形状必须在 NanoVG 的帧里画（它就是 GL）；</li>
 *   <li>文字必须在那帧<b>之后</b>才提交，否则会被 NanoVG 的状态改动作废（症状是文字忽有忽无）；</li>
 * </ul>
 * 所以这里把文字调用<b>先登记、在 {@link #end()} 里统一补画</b>：控件只管"我要在这儿写一行字"，
 * 顺序由这一处保证。让每个控件自己记住"我的文字要晚一帧"迟早会有人漏。
 *
 * <p>【文字为什么还借原版字形】中文在一个 TTF 里不好办（要么塞 10MB 字体，要么把字形图集
 * 当贴图降级成位图）。卡面内容当初就是这么定的（见 {@code NvgCardPainter}）：形状自绘、
 * 文字交给 MC 自己的字形图集。界面沿同一条边界。
 */
public final class NvgUi implements AutoCloseable {

    private final GuiGraphics gui;
    private final Font font;
    private final NvgCanvas canvas;

    /**
     * 底层的 NvgCanvas。
     *
     * <p>【为什么需要它】Trellis 试点要从外面接进<b>同一个</b> NanoVG 上下文
     * （见 {@code TrellisColumn}）。没有这个入口的话，外面只能自己再 {@code nvgCreate} 一个 ——
     * 那就在一个线程上开出了两台互相不知道对方的状态机。
     */
    public NvgCanvas canvas() {
        return canvas;
    }
    private final MemoryStack stack;
    /**
     * 登记到帧尾的一条绘制：<b>做什么</b> + <b>登记时所在的裁剪框</b>。
     *
     * <p>【名字为什么不叫 {@code Text}】A-24 之前这里只装文字，所以就叫 {@code Text}；
     * A-25 起宿主的<b>自绘盒</b>（MC 物品那一类，见 {@code BoxPainter}）也走这条队列 ——
     * 两类东西"帧尾提交"的需求是同一个，分两条队列只会把 {@link Clip} 那套分组逻辑抄第二遍。
     * 名字跟着事实改掉，免得下一个人读着 {@code texts} 却看见物品被塞进来。
     */
    private record Deferred(Runnable draw, Clip clip) {
    }

    /**
     * 一个裁剪框（屏幕逻辑坐标）。
     * <p>
     * 【为什么延迟提交的东西都要记它】形状在 NanoVG 的帧里当场画掉，而登记的绘制是
     * {@link #close()} 里才提交的 —— 提交时当前裁剪早就变了。不记住登记时那个框，
     * 滚出视口的行会在裁剪失效之后才画出来（文字与自绘盒同病）。
     */
    record Clip(float x, float y, float w, float h) {
    }

    private final List<Deferred> deferred = new ArrayList<>();

    /** 当前的裁剪框（null = 没裁）；形状那一套由 NanoVG 的 save/restore 管。 */
    private Clip clip;
    private final List<Clip> clipStack = new ArrayList<>();

    /**
     * 这一层的不透明度（1 = 不透明）。
     * <p>
     * 【为什么形状和文字要共用它】在 {@link #color} 与文字登记两处各乘一次，调用方就只需要
     * "这一段淡出"一句话 —— 否则"形状淡了、文字还实着"是必然会发生的事（它们本来就是两套
     * 提交路径）。分成两个开关没有意义：谁也不会想只淡形状。
     */
    private float alpha = 1f;

    /** 界面配色。 */
    public final NvgPalette palette;
    /** 本帧鼠标位置（逻辑坐标）与时刻。 */
    public final float mouseX;
    public final float mouseY;
    public final long now;

    private NvgUi(GuiGraphics gui, NvgCanvas canvas, MemoryStack stack, NvgPalette palette,
                  float mouseX, float mouseY, long now) {
        this.gui = gui;
        this.font = Minecraft.getInstance().font;
        this.canvas = canvas;
        this.stack = stack;
        this.palette = palette;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.now = now;
    }

    /** 上下文拿不到只报<b>一次</b> —— 那是初始化期的失败，重试不会变好。 */
    private static boolean unavailableLogged;

    /**
     * 开一帧。<b>调用方必须用 try-with-resources</b>：形状画在 {@link #end()} 里才提交文字，
     * 中间抛异常也要把 GL 状态还回去（{@link NvgCanvas#end()} 自己保证）。
     *
     * @return 上下文不可用时返回 null —— 调用方当作"这一帧没界面"
     */
    public static NvgUi begin(GuiGraphics gui, NvgPalette palette, float mouseX, float mouseY, long now) {
        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg == null || !nvg.valid()) {
            // 【为什么只报一次】本仓自己早写过这条口径：`NvgCanvas.shared()` 的注释是
            // "只尝试一次 —— 每帧重试的代价是每帧一条错误日志，日志会没法看"。但那句话只做在
            // **创建**上，报错留在了调用方：三个 Screen 一帧只调一次，看不出来；而 A-24 的
            // 常驻 HUD 面板进世界后**每帧**都会问到这一句 —— 在那台机器上日志会以 60+/s 的速度
            // 刷同一条 ERROR，顺带毁掉验收用的"0 条 ERROR"判据。（评审逮到的潜伏项：本轮真机
            // 没碰到，因为 NanoVG 起得来。）
            if (!unavailableLogged) {
                unavailableLogged = true;
                PickupCard.LOGGER.error("NanoVG 不可用：自绘界面画不出来（上面的 [nvg] 日志有原因）。"
                        + "这条只报一次：它是初始化期的失败，重试不会变好。");
            }
            return null;
        }
        gui.flush();
        MemoryStack stack = MemoryStack.stackPush();
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        nvg.begin(gui.guiWidth(), gui.guiHeight(), scale);
        return new NvgUi(gui, nvg, stack, palette, mouseX, mouseY, now);
    }

    /** NanoVG 句柄，交给要直接调 NanoVG 的地方（比如卡面预览）。 */
    public long vg() {
        return canvas.handle();
    }

    public MemoryStack stack() {
        return stack;
    }

    public Font font() {
        return font;
    }

    // ------------------------------------------------------------------
    // 形状
    // ------------------------------------------------------------------

    /** 圆角矩形填充。 */
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        nvgBeginPath(vg());
        nvgRoundedRect(vg(), x, y, w, h, Math.max(0f, Math.min(radius, Math.min(w, h) / 2f)));
        nvgFillColor(vg(), color(argb));
        nvgFill(vg());
    }

    /** 一条竖渐变（面板、进度条那种"有厚度"的底）。 */
    public void fillGradient(float x, float y, float w, float h, int topArgb, int bottomArgb) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        nvgBeginPath(vg());
        nvgRoundedRect(vg(), x, y, w, h, palette.radius);
        NVGPaint paint = nvgLinearGradient(vg(), x, y, x, y + h,
                color(topArgb), color(bottomArgb), NVGPaint.mallocStack(stack));
        nvgFillPaint(vg(), paint);
        nvgFill(vg());
    }

    /** 圆角矩形描边（路径内缩半个线宽，整条线才落在框内）。 */
    public void strokeRoundRect(float x, float y, float w, float h, float radius, int argb) {
        float half = palette.outlineWidth / 2f;
        if (w <= palette.outlineWidth || h <= palette.outlineWidth) {
            return;
        }
        nvgBeginPath(vg());
        nvgRoundedRect(vg(), x + half, y + half, w - palette.outlineWidth, h - palette.outlineWidth,
                Math.max(0f, radius - half));
        nvgStrokeWidth(vg(), palette.outlineWidth);
        nvgStrokeColor(vg(), color(argb));
        nvgStroke(vg());
    }

    /** 正圆填充（滑块、开关的钮）。 */
    public void circle(float cx, float cy, float r, int argb) {
        if (r <= 0f) {
            return;
        }
        nvgBeginPath(vg());
        nvgCircle(vg(), cx, cy, r);
        nvgFillColor(vg(), color(argb));
        nvgFill(vg());
    }

    /** 一块面板：底 + 描边，界面上的"容器"都是它。 */
    public void panel(float x, float y, float w, float h) {
        fillRoundRect(x, y, w, h, palette.radius, palette.panel);
        strokeRoundRect(x, y, w, h, palette.radius, palette.outline);
    }

    /** 一块"井"：控件的底（悬停/pressed 各一档颜色，由调用方给）。 */
    public void well(float x, float y, float w, float h, int argb) {
        fillRoundRect(x, y, w, h, palette.radius, argb);
        strokeRoundRect(x, y, w, h, palette.radius, palette.outline);
    }

    // ------------------------------------------------------------------
    // 文字（先登记，end() 之后统一提交）
    // ------------------------------------------------------------------

    public void text(String s, float x, float y, int argb) {
        register(() -> gui.drawString(font, s, Math.round(x), Math.round(y), fade(argb, alpha), true));
    }

    /** 居中写一行（x 给中心）。 */
    /** 左对齐写一行，超过 maxW 就整体缩小到装得下（左缘与文字垂直中心为锚）——见 textCenteredFitted。 */
    public void textFitted(String s, float x, float y, int argb, float maxW) {
        register(() -> {
            float tw = font.width(s);
            int color = fade(argb, alpha);
            if (tw <= maxW || tw <= 0f) {
                gui.drawString(font, s, Math.round(x), Math.round(y), color, true);
                return;
            }
            float k = maxW / tw;
            var pose = gui.pose();
            pose.pushPose();
            pose.translate(x, y + font.lineHeight / 2f, 0f);
            pose.scale(k, k, 1f);
            gui.drawString(font, s, 0, Math.round(-font.lineHeight / 2f), color, true);
            pose.popPose();
        });
    }

    public void textCentered(String s, float centerX, float y, int argb) {
        register(() -> gui.drawString(font, s, Math.round(centerX - font.width(s) / 2f),
                Math.round(y), fade(argb, alpha), true));
    }

    /**
     * 居中写一行，<b>超过 maxW 就整体缩小到装得下</b>（以文字垂直中心为锚）。
     * <p>【为什么存在】语言一换（英文 "Same name + enchants" / "Long name"），文案比
     * 中文宽一截：按钮盒不能为它变宽（行宽是布局契约），截断又会让循环选项认不出来
     * —— 缩字是唯一不破坏布局的兜底。日常（zh / 宽窗口）k=1，一个像素都不动。
     */
    public void textCenteredFitted(String s, float centerX, float y, int argb, float maxW) {
        register(() -> {
            float tw = font.width(s);
            int color = fade(argb, alpha);
            if (tw <= maxW || tw <= 0f) {
                gui.drawString(font, s, Math.round(centerX - tw / 2f), Math.round(y), color, true);
                return;
            }
            float k = maxW / tw;
            var pose = gui.pose();
            pose.pushPose();
            pose.translate(centerX, y + font.lineHeight / 2f, 0f);
            pose.scale(k, k, 1f);
            gui.drawString(font, s, Math.round(-tw / 2f), Math.round(-font.lineHeight / 2f),
                    color, true);
            pose.popPose();
        });
    }

    /** 右对齐写一行（x 给右缘）。 */
    public void textRight(String s, float rightX, float y, int argb) {
        register(() -> gui.drawString(font, s, Math.round(rightX - font.width(s)),
                Math.round(y), fade(argb, alpha), true));
    }

    /**
     * 把一段绘制<b>登记到帧尾</b>，连同它此刻所在的裁剪框。
     *
     * <p>【为什么要延迟】{@link #close()} 里先 {@code canvas.end()} 收掉 NanoVG 那一帧，
     * 之后才按裁剪框分组提交这些登记项。原版批次（MC 的字，以及宿主自绘盒里的物品渲染）
     * 必须在 NanoVG 帧<b>结束之后</b>跑 —— 宿主的 {@code NvgCardPainter} 就是这个顺序
     * （先画外壳，{@code nvg.end()} 之后再走原版批次）。
     *
     * <p>【为什么是 public】本来只有本类的几个 {@code text*} 在用；A-25 起宿主自绘盒
     * （{@code BoxPainter} 的实现）也从外面登记。**登记进来就自动带上当时的裁剪框** ——
     * 这是它比"自己找个时机画"强的地方（坑 12：延迟绘制不记裁剪框，就会画在裁剪失效之后）。
     */
    public void register(Runnable draw) {
        deferred.add(new Deferred(draw, clip));
    }

    // ------------------------------------------------------------------
    // 不透明度（一段整体的淡入淡出）
    // ------------------------------------------------------------------

    /**
     * 设定这一段的不透明度（0..1）。<b>画完记得调回 1</b> —— 它只影响之后画的那些东西。
     * <p>为什么给整层：换页/换样例是"一整块内容换了"，逐控件改色要把每个颜色乘一遍，
     * 漏一个就是"有一行没淡"。
     */
    public void alpha(float value) {
        this.alpha = Easing.clamp01(value);
    }

    /**
     * ARGB 乘上一个不透明度（纯函数，单测钉住）。
     * <p>只改 alpha 通道，RGB 一个位都不动 —— 淡出不该顺便变色。
     */
    public static int fade(int argb, float alpha) {
        if (alpha >= 1f) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(alpha));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    /**
     * 两个 ARGB 之间按 {@code t} 插值（0 = 全 {@code from}，1 = 全 {@code to}）。
     * <p>【为什么需要它】"悬停时标签变亮"本质是<b>颜色的连续变化</b>，而控件里只有"按下的那一档
     * 颜色"。做成两档会在眼睛看到的一瞬间跳 —— 而跳变正是这些短动画要消掉的东西。
     */
    public static int mix(int from, int to, float t) {
        float k = Easing.clamp01(t);
        if (k <= 0f) {
            return from;
        }
        if (k >= 1f) {
            return to;
        }
        int a = channel(from, 24) + Math.round((channel(to, 24) - channel(from, 24)) * k);
        int r = channel(from, 16) + Math.round((channel(to, 16) - channel(from, 16)) * k);
        int g = channel(from, 8) + Math.round((channel(to, 8) - channel(from, 8)) * k);
        int b = channel(from, 0) + Math.round((channel(to, 0) - channel(from, 0)) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    // ------------------------------------------------------------------
    // 裁剪（滚动的列表、预览面板用）
    // ------------------------------------------------------------------

    /**
     * 开一个裁剪框。<b>必须配对 {@link #popClip()}</b>。
     * <p>
     * 【为什么要一次设两套】形状是 NanoVG 画的（{@code nvgScissor}），文字是原版批次画的
     * （{@code gui.enableScissor}）—— 只设一套的症状是"形状被裁了、文字糊在外面"，
     * 而那种半对的样子最难查。所以这里一次把两边都设上。
     */
    public void pushClip(float x, float y, float w, float h) {
        clipStack.add(clip);
        clip = new Clip(x, y, Math.max(0f, w), Math.max(0f, h));
        nvgSave(vg());
        nvgScissor(vg(), clip.x(), clip.y(), clip.w(), clip.h());
    }

    /** 收掉最近一次 {@link #pushClip}。 */
    public void popClip() {
        if (clipStack.isEmpty()) {
            return;
        }
        nvgRestore(vg());
        clip = clipStack.remove(clipStack.size() - 1);
    }

    public int textWidth(String s) {
        return font.width(s);
    }

    // ------------------------------------------------------------------

    /** 收帧：先关 NanoVG，再把登记的文字交给原版批次。 */
    @Override
    public void close() {
        try {
            canvas.end();
        } finally {
            stack.close();
        }
        // 文字按"登记时的裁剪框"分组提交：换框前先把上一段的批次冲掉，否则裁剪会被套到
        // 先登记的那些行上（原版的 enableScissor 只影响之后提交的东西）
        Clip active = null;
        try {
            for (Deferred d : deferred) {
                Clip want = d.clip();
                boolean same = want == null ? active == null : want.equals(active);
                if (!same) {
                    if (active != null) {
                        gui.disableScissor();
                    }
                    active = want;
                    if (active != null) {
                        gui.enableScissor(Math.round(active.x()), Math.round(active.y()),
                                Math.round(active.x() + active.w()),
                                Math.round(active.y() + active.h()));
                    }
                }
                d.draw().run();
            }
        } finally {
            // 【为什么是 finally】任何一条登记项抛异常，都要把 scissor 关掉再往上抛 ——
            // 开着留到本帧余下的所有原版绘制上，症状是"后面画的东西整片被裁"，
            // 而且离抛出点很远，查起来要命（A-25 的评审顺手指出这条）。
            if (active != null) {
                gui.disableScissor();
            }
        }
    }

    /** ARGB -> NanoVG 要的 RGBA 分量（顺带把这一层的不透明度乘进去）。 */
    private NVGColor color(int argb) {
        int a = fade(argb, alpha);
        return nvgRGBA((byte) (a >> 16), (byte) (a >> 8), (byte) a, (byte) (a >>> 24),
                NVGColor.mallocStack(stack));
    }
}
