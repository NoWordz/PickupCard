package com.niuqu.pickupcard.render.nvg;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.rarity.RarityAccent;
import com.niuqu.pickupcard.render.BatchStats;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.FadingItemBuffers;
import com.niuqu.pickupcard.style.BodyGeometry;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.RevealWindow;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
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
import static org.lwjgl.nanovg.NanoVG.nvgRotate;
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
 * 2026-09-17 之前，同一张卡面有四份实现：原版渲染、HTML 卡面当贴图、SDF 着色器图层、
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

    /** 稀有度微光往外扩的量与软边宽度。 */
    private static final float GLOW_SPREAD = 2f;
    private static final float GLOW_FEATHER = 3f;

    /** "引擎没了"这件事每次会话只该在屏幕上说一次，日志里也只需要一条。 */
    private boolean reportedMissing;

    /**
     * 分段计时开关（{@code -PpcProfile=1} → {@code -Dpickupcard.profile=1}）：只打头几帧，
     * 用来定位一次性开销在哪。
     * <p>【为什么不用 {@code Boolean.getBoolean}】它只认 "true"（大小写不敏感），
     * 给个 1 会静默地当成关 —— 而 {@code -Pxx=1} 是最顺手的写法。
     */
    private static final boolean PROFILE = isOn(System.getProperty("pickupcard.profile"));
    private int profileFrames;

    /** 分段计时开关的只读口：{@link NvgCardContent} 与 {@code CardStage} 也要用它门控自己的 nanoTime。 */
    public static boolean profiling() {
        return PROFILE;
    }

    private static boolean isOn(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1")
                || value.equalsIgnoreCase("on"));
    }

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

        long t0 = System.nanoTime();
        BatchStats.countFlush();
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
        boolean profile = PROFILE && profileFrames < 6;
        NvgCanvas.endProfile = profile;
        long flushDoneAt = profile ? System.nanoTime() : 0L;
        nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
        long beginDoneAt = profile ? System.nanoTime() : 0L;
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
                // 【退场缩放单点出处】SCALE 档的整卡收缩由 cardScaleOf 一处给出，与画布缩放、
                // 脉冲相乘（不是替代，不新增变换栈）；内容路（NvgCardContent#paint 的
                // effScale）同吃这一个数 —— 两份实现必有一份错（2026-09-20 镜像 bug 的教训）。
                float exitScale = cardScaleOf(canvas, slot);
                float w0 = slot.width() / cardScale;
                float h0 = slot.height() / cardScale;
                // 【纵向位移单点出处】DROP 入场 / FALL 退场的竖向位移由 verticalShiftOf 一处给出，
                // 加在"卡心平移"这层（缩放之前）= 屏幕逻辑 px，不随布局缩放变形。
                // 内容路（NvgCardContent#paint 的 pose.translate）同吃这一个数 ——
                // 两份实现必有一份错（2026-09-20 镜像 bug 的教训）。
                nvgTranslate(vg, slot.x() + slot.width() / 2f,
                        slot.y() + slot.height() / 2f + verticalShiftOf(canvas, slot));
                nvgScale(vg, cardScale * pulse * exitScale, cardScale * pulse * exitScale);
                // 【sway 单点出处】稳态摇摆由 swayAngleOf 一处给出角度；当前原点在卡心，
                // 与缩放变换同一个枢轴 —— 内容路 mulPose 同角度、同枢轴（卡心 + 位移），同吃一个数。
                // sway 非 0 时窗口必然全开（入场完且非退场），旋转不会让窗口裁掉任何外壳。
                float sway = swayAngleOf(canvas, slot);
                if (sway != 0f) {
                    nvgRotate(vg, (float) Math.toRadians(sway));
                }
                paintShell(vg, style, -w0 / 2f, -h0 / 2f, w0, h0,
                        accentOf(card, style.accents()), canvas.barOf(slot.view()),
                        bodyShiftOf(canvas, slot, style, rise), rise, glowStrengthOf(card),
                        glowScaleOf(canvas, slot), shimmerOf(canvas, slot),
                        canvas.layout().mirrorCard(),
                        windowOf(canvas, slot, style, rise));
                nvgRestore(vg);
            }
        } finally {
            nvg.end();
        }
        long shellDoneAt = profile ? System.nanoTime() : 0L;

        // 内容排在 NanoVG 之后：它画在卡面之上（用的是同一批屏幕坐标）。
        // 图标与文字都在这一路 —— 图标每帧原版现渲，文字走原版字形（见 NvgCardContent）。
        NvgCardContent.profileIconUs = 0L;
        NvgCardContent.profileTextUs = 0L;
        for (CardSlot slot : slots) {
            NvgCardContent.paint(gui, canvas, slot, font);
        }

        if (profile) {
            profileFrames++;
            // 分段账：flush / begin / 逐卡外壳 / end（再拆成 NanoVG 自己的 flush 与 MC 状态恢复）
            // / 图标 / 文字 —— 首帧那几十毫秒到底记在哪一段，只有拆到这一步才看得出。
            PickupCard.LOGGER.info("[profile] cards={} flush={}us begin={}us 逐卡={}us end={}us"
                            + "（其中 nvgEndFrame={}us 状态恢复={}us）图标={}us 文字={}us",
                    slots.size(), (flushDoneAt - t0) / 1_000L, (beginDoneAt - flushDoneAt) / 1_000L,
                    (shellDoneAt - beginDoneAt) / 1_000L, (System.nanoTime() - shellDoneAt) / 1_000L,
                    NvgCanvas.endFrameUs, NvgCanvas.restoreUs,
                    NvgCardContent.profileIconUs, NvgCardContent.profileTextUs);
        }
    }

    /**
     * 提前把"这张卡第一次真的被画出来"那一帧的账付掉 —— <b>由进世界后的第一帧调用</b>。
     *
     * <p>【为什么必须有它】懒创建把这些钱全记在了<b>第一次拾取那一帧</b>上，实测那一帧要
     * <b>62ms</b>（5 张卡：外壳 37.5ms + 图标 24.5ms + 文字 1.9ms；用户实例里第一张卡同样
     * 要 51ms），玩家读到的就是「一捡东西就卡一下」。钱不能省，但可以挪到进世界那几帧去付。
     *
     * <p>【为什么"建上下文 + 空帧"不够】这才是关键教训：{@code nvgCreate} 只把壳建起来，
     * 驱动是<b>第一次真正画东西</b>时才编着色器/JIT 管线的 —— 空跑 begin/end 实测仍然
     * 留下 37ms 在第一次绘制上。所以这里必须真画，而且要把用到的每条绘制路径都走到：
     * 圆角矩形填充、渐变填充（框底）、描边、boxGradient（微光）、线性渐变（扫光）、
     * 以及镜像分支。
     *
     * <p>【为什么看不见】外壳用 {@code nvgGlobalAlpha(0)} 画在屏幕内（透明度 0 = 逐像素
     * 无变化，但绘制仍然真的发生）；图标不能这么干（无混合层忽略 alpha），所以挪到屏幕外
     * —— 顶点全在裁剪体外，驱动照样得把管线准备好。
     *
     * @return true = 还有后续步骤，调用方下一帧继续调
     */
    public static boolean warmUpStep(GuiGraphics gui, StyleModel style) {
        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg == null || !nvg.valid()) {
            return false;
        }
        long t0 = System.nanoTime();
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        Font font = Minecraft.getInstance().font;
        if (warmStep == 0) {
            warmShell(gui, nvg, style, guiScale);
        } else if (warmStep == 1) {
            // 文字：字形图集的上传。屏幕外，用的是一串常见字符。
            gui.drawString(font, "0.9K +Common", -20_000, -20_000,
                    style.nameColor(), false);
        } else if (warmStep == 2) {
            // 【字体测量预热】第一张卡那帧 layout 会花 10ms 以上 —— 那不是布局公式贵，是
            // {@code font.width(name)} 第一次逼着字形按需栅格化（每个字符一次）。把可打印
            // ASCII 全集 + 常见符号的量宽提前跑掉，第一次拾取那一帧的量宽就全是缓存命中。
            // （中文走 Unicode 图集、量一次贵一次，这里按量级覆盖 ASCII —— 英文名占大头。）
            warmFontMetrics(font);
        } else {
            ItemStack[] icons = warmIcons();
            int from = (warmStep - 3) * ICONS_PER_STEP;
            for (int i = from; i < Math.min(icons.length, from + ICONS_PER_STEP); i++) {
                FadingItemBuffers.drawIcon(gui, icons[i], -20_000f, -20_000f,
                        style.iconSize(), 1f, true);
            }
        }
        warmStep++;
        warmTotalUs += (System.nanoTime() - t0) / 1_000L;
        int totalSteps = 3 + (warmIcons().length + ICONS_PER_STEP - 1) / ICONS_PER_STEP;
        if (warmStep < totalSteps) {
            return true;
        }
        PickupCard.LOGGER.info("[预热] 引擎的首次绘制在进世界时分 {} 帧付掉：合计 {}us"
                        + "（外壳首轮={}us → 次轮={}us，这就是「这笔账真的付掉了」的证据）",
                totalSteps, warmTotalUs, warmShellFirstUs, warmShellSecondUs);
        return false;
    }

    /**
     * 把量宽会碰到的字符提前全部量一遍。
     * <p>【为什么分段只放一个字符串】{@code font.width} 是字形图集的按需栅格化入口，
     * 第一张卡那一帧的 10ms layout 就是这里欠的账（实测 16:41 轮 layout=10.6ms）。
     * ASCII 可打印字符 + 数字格式会用到的符号一起量掉；中文名的量宽在玩家切中文包时才
     * 发生，那次仍有一次性开销，但它是"第一次出现中文"而不是"第一次捡东西"。
     */
    private static void warmFontMetrics(Font font) {
        StringBuilder sb = new StringBuilder(128);
        for (char c = 32; c < 127; c++) {
            sb.append(c);
        }
        sb.append("+×KMBk…");        // 数量格式与省略号
        for (int i = 0; i < sb.length(); i++) {
            font.width(String.valueOf(sb.charAt(i)));
        }
        font.width(sb.toString());   // 整串再量一次（缓存命中，几乎免费 —— 保的是行为一致）
    }

    /** 预热分帧进度：0 = 还没开始；每帧 +1，走到 {@code totalSteps} 即完成。 */
    private static int warmStep;
    private static long warmTotalUs;
    private static long warmShellFirstUs;
    private static long warmShellSecondUs;

    /**
     * 预热第一段：外壳。<b>为什么画两轮</b> —— 第一轮付"第一次绘制"（驱动编着色器/JIT 管线），
     * 第二轮验证它真的被付掉了。实测 23ms → 0.1ms，这是"预热有效"的硬证据，也是
     * "只建上下文不画东西等于没预热"那个教训的量尺。
     */
    private static void warmShell(GuiGraphics gui, NvgCanvas nvg, StyleModel style, float guiScale) {
        for (int round = 0; round < 2; round++) {
            long roundT0 = System.nanoTime();
            // 【顺序必须与真实一帧一致】外壳在 NVG 帧内、图标与文字在帧外 —— 把图标画进
            // 帧内会让真实那一帧照样贵：两条路径的 GL 状态序列不同，驱动对"第一次"的判定
            // 也就不同。要挪走的那笔账，必须按原样再走一遍才算付过。
            nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
            try {
                long vg = nvg.handle();
                nvgSave(vg);
                nvgGlobalAlpha(vg, 0f);
                float h = style.boxHeight();
                // 【为什么要画满一摞，而不是两张】"第一次"不只是"第一次画"：NanoVG 的路径/顶点
                // 缓冲会随一帧里的路径数增长而扩容，而入场那一帧是整摞卡一起画 —— 预热只画两张，
                // 扩容那笔账照样落在玩家那一帧上。实测（2026-09-20 性能审计）入场首帧的外壳
                // 是稳态的十倍上下（2600~5800us vs 150~240us），而首帧 layout 已经由文本预热
                // 压到 0.3ms —— 剩下这块就是它。
                // 两条几何分支（常规/镜像）、不同宽度与档位色都过一遍；微光与扫光各是一种 paint。
                var warmAccents = style.accents();
                int[] warmColors = {warmAccents.common(), warmAccents.rare(), warmAccents.epic(),
                        warmAccents.xp(), warmAccents.overflow()};
                for (int i = 0; i < warmColors.length; i++) {
                    paintShell(vg, style, 4f, 4f + (h + 2f) * i, 120f + i * 7f, h, warmColors[i],
                            1f, 0f, 1f, 1f, 1f, 0.3f, (i % 2) == 1,
                            new RevealWindow(0f, 200f));
                }
                nvgRestore(vg);
            } finally {
                nvg.end();
            }
            long roundUs = (System.nanoTime() - roundT0) / 1_000L;
            if (round == 0) {
                warmShellFirstUs = roundUs;
            } else {
                warmShellSecondUs = roundUs;
            }
        }
    }

    /**
     * 预热用的图标：把"卡上会出现的东西"按<b>渲染层与模型形态</b>分类各来一发 ——
     * 平贴图（钻石剑/翅膀）、方块模型（石头/信标/龙蛋）、带 NBT 的实体模型（附魔书）、
     * 以及经验卡固定用的下界之星。
     * <p>每类在驱动里都是"第一次画才编管线 + 现烘焙模型"，所以只热其中一类，另一类照样
     * 把账留到第一次拾取那一帧（实测漏掉三类时首帧仍要 27ms）。
     * <p>【为什么不在类加载期就建】这里任何一个 {@code new ItemStack} 都会经 codec 拖出
     * 注册表初始化（Not bootstrapped）：游戏进程里无感（类加载时引导早已完成），但本类的
     * 几个纯时间函数因此没法被无头单元测试加载。预热本来就在进世界后才用 —— 按预热自己的
     * 哲学"账进世界后付"，推迟到第一次预热再建。
     */
    private static ItemStack[] warmIcons() {
        if (WARM_ICONS == null) {
            WARM_ICONS = new ItemStack[] {
                    new ItemStack(net.minecraft.world.item.Items.STONE),
                    new ItemStack(net.minecraft.world.item.Items.COMMAND_BLOCK),
                    new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD),
                    new ItemStack(net.minecraft.world.item.Items.ELYTRA),
                    new ItemStack(net.minecraft.world.item.Items.BEACON),
                    new ItemStack(net.minecraft.world.item.Items.DRAGON_EGG),
                    new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK),
                    new ItemStack(net.minecraft.world.item.Items.NETHER_STAR),
            };
        }
        return WARM_ICONS;
    }

    private static ItemStack[] WARM_ICONS;

    /** 每个预热帧画几个图标：一个个错开付，避免一次 50ms 的集中卡顿。 */
    private static final int ICONS_PER_STEP = 2;

    /**
     * 一张卡的全部矢量部分。退场淡出不在这里 —— 调用方用 {@code nvgGlobalAlpha} 一笔带过，
     * 这里只管"浓度随入场进度"的那部分（影子、竖条）。
     *
     * @param x,y      卡片左缘 / 顶边（屏幕逻辑坐标）
     * @param barFill  竖条展开比例 0~1
     * @param bodyShift 两个内容框的横向偏移（内容从竖条后面滑出来用）；竖条自己不动，
     *                  它是"洞口"，所以只有内容偏移。退场「火车退回」的位移也从这里进。
     *                  镜像卡片时位移方向已由 {@link #bodyShiftOf} 翻转。
     * @param rise     内容出现进度 0~1。影子浓度与微光都按它给 —— 用户报过"影子一出来就是
     *                 满的，看着像影子先到、卡片后到"
     * @param glowStrength 微光强度 0~1（{@link #glowStrengthOf} 给：按稀有度档位爬阶梯）。
     *                     0 = 这张卡没有微光
     * @param glowScale 微光呼吸系数（0.4~1.0，关闭呼吸恒为 1）
     * @param shimmer  入场扫光进度：0 = 不画；(0,1) = 光带走到哪（{@link #shimmerOf} 给）
     * @param mirror   镜像卡片（2026-09-20 用户定的开关）：竖条在最右，往左依次是图标格、
     *                 信息框；内容区的几何全部以卡右缘为基准反算
     * @param window   隧道口：内容能被看见的那一段。**偏移必须有它配套**：只有偏移没有裁剪，
     *                 两个框就会从竖条前面滑过去 —— 真机上看就是"卡片穿透竖条"。
     *                 消失方式「拉幕收拢」也从这里进：退场时窗口朝竖条那侧收。
     */
    public static void paintShell(long vg, StyleModel style, float x, float y, float cardW, float cardH,
                                  int accent, float barFill, float bodyShift, float rise,
                                  float glowStrength, float glowScale, float shimmer, boolean mirror,
                                  RevealWindow window) {
        float gap = style.gap();
        float barW = style.barWidth();
        // 三个框的自然位置：与内容路共用同一份几何（BodyGeometry）—— 镜像这种"整套坐标
        // 反着来"的改动，两份实现一定会漏掉一边（2026-09-20 用户报「文字动画没镜像」）。
        BodyGeometry body = BodyGeometry.of(cardW, cardH, barW, gap, mirror);
        // 内容区左缘（屏幕坐标）：微光与扫光都从它起算
        float bodyLeft = x + body.bodyLeft();
        float radius = Math.min(style.cornerRadius(), cardH / 2f);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 竖条在"洞口"那侧，不受窗口影响，单独画
            bar(vg, stack, style, mirror ? x + cardW - barW : x, y, cardH, radius, accent, barFill);

            // 【两个内容框必须在窗口里画】偏移让框朝洞口回缩，窗口把多出来的那截切掉，
            // 合起来才是"从隧道口冒出来"。用 save/restore 包住：scissor 是 NanoVG 的
            // 状态，留着会影响后面每一张卡。
            nvgSave(vg);
            nvgScissor(vg, x + window.left(), y, window.width(), cardH);
            if (window.width() > 0.01f) {
                box(vg, stack, style, x + body.iconLeft() + bodyShift, y, cardH, cardH, radius);
                if (body.infoWidth() > 0f) {
                    box(vg, stack, style, x + body.infoLeft() + bodyShift, y, body.infoWidth(),
                            cardH, radius);
                }
                // 扫光在框之后、微光之前：它要照亮的是框面（在框上才读得出"掠过"），
                // 又必须被窗口裁着（入场未完成时不许越出洞口）。镜像时进度取反，
                // 光带就从右往左扫——与"从竖条侧出发"的镜像语义一致
                if (shimmer > 0f) {
                    shimmerBand(vg, stack, style, bodyLeft, y, body.bodyWidth(), cardH, radius,
                            mirror ? 1f - shimmer : shimmer);
                }
            }
            nvgRestore(vg);

            // 微光叠在外壳之"上"：画在框之前会被底色盖掉，看起来就是没画。
            // glowScale 是呼吸系数（0.4~1.0，关闭呼吸时恒 1）；glowStrength 是档位阶梯；
            // ramp 让微光在入场后半程淡入——从前 rise 过 0.5 直接满亮弹出，生硬
            // （2026-09-20 用户报"光圈没有跟随动画、没有淡入淡出"）。
            // 【x 跟随 bodyShift】内容滑出时光圈跟着框走，而不是钉在原地等内容撞进来。
            // 退场的淡出不用这里管：整张卡的外壳在 nvgGlobalAlpha 里，随退场一起淡。
            if (glowStrength > 0f && rise > 0.45f && style.glowAlpha() > 0) {
                float ramp = Easing.clamp01((rise - 0.45f) / 0.4f);
                softBox(vg, stack, bodyLeft + bodyShift - GLOW_SPREAD, y - GLOW_SPREAD,
                        body.bodyWidth() + GLOW_SPREAD * 2f, cardH + GLOW_SPREAD * 2f,
                        radius + GLOW_SPREAD,
                        GLOW_FEATHER, withAlpha(accent,
                                Math.round(style.glowAlpha() * glowScale * glowStrength * ramp)));
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
    static RevealWindow windowOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        // 窗口是"卡内坐标"，所以要用未缩放的宽度（它在变换后的空间里被解释）
        float w0 = slot.width() / canvas.scale();
        // 【镜像卡片】竖条在右缘：窗口贴着竖条左缘、从右往左长（RevealWindow 的镜像分支）
        boolean mirror = canvas.layout().mirrorCard();
        RevealWindow win = RevealWindow.of(style.barWidth(), style.gap(), w0,
                canvas.layout().appearMode() == LayoutSettings.Appear.CLIP, rise, mirror);
        // 【消失方式＝拉幕收拢】可见范围收窄：拉幕入场的逆放。与入场窗口取 min ——
        // 万一"还没展开完就开始退"也不会越宽。收拢永远朝竖条那侧合拢（镜像时锚定边
        // 是窗口右缘，RevealWindow 的镜像分支已经把方向算对了）。
        if (canvas.layout().exitMode() == LayoutSettings.Exit.WIPE && slot.view().exiting()) {
            float full = RevealWindow.contentWidth(w0, style.barWidth(), style.gap());
            win = new RevealWindow(win.left(), Math.min(win.width(),
                    full * (1f - Easing.clamp01(canvas.exitOf(slot.view())))));
        }
        return win;
    }

    /** 内容横向滑动量：CLIP 是"窗口变宽、内容不动"，另一模式是内容从竖条后面平移出来。 */
    static float bodyShiftOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        boolean mirror = canvas.layout().mirrorCard();
        float bodyW = Math.max(0f, slot.width() / canvas.scale() - style.barWidth() - style.gap());
        // 【镜像=方向因子】非镜像内容从竖条（左）后面往右冒，位移是负的；镜像竖条在右，
        // 内容往左冒，位移取正。退场「火车退回」同样乘这个因子，一份公式两种朝向。
        int dir = mirror ? 1 : -1;
        // 【BOUNCE 与 SLIDE 同路线不同曲线】位移公式一模一样，只把进度换成 easeOutBack(enterOf)：
        // easeOutBack 会越过 1（峰值≈1.1），(1-p) 随之翻负 —— 内容冲过终点再被拉回来，就是过冲回弹。
        // 不能吃 rise（contentOf 已套 CONTENT_CURVE），BOUNCE 要的是原始入场钟（enterOf）自己套曲线。
        // 窗口 windowOf 不跟着动：仍按 rise 展开，过冲的内容在满宽窗口内不会被裁掉。
        float progress = canvas.layout().appearMode() == LayoutSettings.Appear.BOUNCE
                ? Easing.easeOutBack(canvas.enterOf(slot.view())) : rise;
        float shift = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP
                ? 0f : dir * (1f - progress) * bodyW;
        // 【消失方式＝火车退回】内容整块平移回竖条后面：火车入场的逆放。窗口把靠竖条
        // 那侧裁住，视觉就是"倒车回隧道"。
        if (canvas.layout().exitMode() == LayoutSettings.Exit.TRAIN && slot.view().exiting()) {
            shift += dir * canvas.exitOf(slot.view()) * bodyW;
        }
        return shift;
    }

    /**
     * 这一帧卡片的纵向位移（屏幕逻辑 px，正 = 向下）：DROP 入场从锚线上方掉落，
     * FALL 退场向下加速坠。两个分支相加 —— 一张卡理论上可以边落边坠。
     * <p>
     * 【为什么必须是这一个函数】2026-09-20 镜像 bug 的教训：同一份几何写两遍必有一份错。
     * 纵向位移有两路消费者 —— 外壳（NanoVG 的 {@code nvgTranslate}）与内容（原版批次 pose，
     * {@code NvgCardContent#paint}）—— 两边<b>必须同吃这一个数</b>：都调用本函数，
     * 谁也不许自己另算一份。位移加在"卡心平移"那一层（缩放之前），所以单位是屏幕逻辑 px，
     * 不随布局缩放变形。
     * <p>
     * 【DROP 为什么吃 enterOf 而不是 rise】同 BOUNCE：contentOf 已套过内容曲线，
     * 掉落要的是原始入场钟自己套 easeOutBack —— 越过 1 的那截就是落地小弹。
     * FALL 只在真的退场时给（easeInQuad：慢起快收 = 加速下坠）；未入场完/未退场恒为 0。
     */
    static float verticalShiftOf(CardCanvas canvas, CardSlot slot) {
        LayoutSettings ls = canvas.layout();
        float drop = 24f;
        float s = 0f;
        if (ls.appearMode() == LayoutSettings.Appear.DROP) {
            s -= (1f - Easing.easeOutBack(canvas.enterOf(slot.view()))) * drop; // 上方落下 + 过冲小弹
        }
        if (ls.exitMode() == LayoutSettings.Exit.FALL && slot.view().exiting()) {
            s += Easing.easeInQuad(canvas.exitOf(slot.view())) * drop;          // 加速下坠
        }
        return s;
    }

    /**
     * 这一帧整卡的缩放因子：SCALE 退场时 1 → 0.15（easeInQuad 收敛，慢起快收地缩没），
     * 其余恒 1。终点 0.15 而不是 0：缩成竖条那么细再随 alpha 消失，比缩到无更"收起"。
     * <p>
     * 【与画布缩放的关系】乘法，不是替代：外壳 {@code nvgScale(S·p)}/{@code nvgScale(S·p·本值)}、
     * 内容 {@code effScale = S·p·本值} —— 不新增变换栈，只是把现成的缩放因子多乘一个。
     * 外壳与内容【必须】同吃这一个数（2026-09-20 镜像 bug 的教训）。
     */
    static float cardScaleOf(CardCanvas canvas, CardSlot slot) {
        LayoutSettings ls = canvas.layout();
        if (ls.exitMode() == LayoutSettings.Exit.SCALE && slot.view().exiting()) {
            return 1f - 0.85f * Easing.easeInQuad(canvas.exitOf(slot.view()));
        }
        return 1f;
    }

    /**
     * 停留期的摇摆角（度）：±1.2° 正弦往复，按 key 散列错开相位（{@link #glowScaleOf} 同款
     * 手法）—— 一摞卡不会齐步摇摆，那是迪厅不是"活着"。
     * <p>
     * 【只在稳态摇】sway 关着、还没入场完（{@code contentOf < 1}，卡还在演入场）、
     * 或正在退场（退场有自己的戏）时恒 0 —— 摇摆是"无事发生"时的心跳，不跟别的动画抢戏。
     * <p>【接线】外壳 {@code nvgRotate} 与内容 {@code mulPose(Axis.ZP…)} 同吃这一个数、
     * 同以卡心为枢（两侧枢轴方式与现有缩放变换一致）—— 一处出角度，两处消费。
     */
    static float swayAngleOf(CardCanvas canvas, CardSlot slot) {
        LayoutSettings ls = canvas.layout();
        if (!ls.swayEnabled() || canvas.contentOf(slot.view()) < 1f || slot.view().exiting()) {
            return 0f;
        }
        float phase = (slot.view().key().hashCode() & 0xFFFF) / 65536f;
        double wave = Math.sin((canvas.now() % 100_000L) / 2000.0 * 2.0 * Math.PI
                + phase * 2.0 * Math.PI);
        return 1.2f * (float) wave;
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
     * 退场不透明度（1 → 0）。入场一律是 1 —— 新卡是"原地出现"的，不淡入。
     * <p>
     * 【为什么退场<b>没有</b>位移】退役那张确实"升一格 + 淡出"，但那"一格"不是画上去
     * 的：它还在 live 里，布局会把它排到堆顶<b>上面</b>那一行（见 {@code StackLayout}），
     * {@code CardMove} 再用 340ms 把它推上去。这里要是再加一段位移，它就要动两格了。
     * 曲线取 easeOutCubic：快出慢停，{@code Easing} 里本来就注着"透明度的默认选择"。
     */
    static float exitAlphaOf(CardCanvas canvas, CardSlot slot) {
        return canvas.exitAlphaOf(slot.view());
    }

    static int accentOf(Inbox.Card card, StyleModel.Accents accents) {
        // 【为什么用 if 而不是 switch】1.20.1 这一支是 Java 17，模式匹配的 switch 还是预览特性
        if (card.content() instanceof CardContent.Item item) {
            return RarityAccent.of(item.stack(), accents);
        }
        return card.content() instanceof CardContent.Overflow
                ? RarityAccent.overflow(accents) : RarityAccent.xp(accents);
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

    private static int withAlpha(int argb, int alpha) {
        return (Math.min(255, Math.max(0, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** 主题里的 ARGB -> NanoVG 要的 RGBA 分量。 */
    private static NVGColor color(MemoryStack stack, int argb) {
        return nvgRGBA((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24),
                NVGColor.mallocStack(stack));
    }
}
