package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.render.nvg.ui.HudStatusBar;
import com.niuqu.pickupcard.render.nvg.ui.McGlyphPainter;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import dev.e33.trellis.tokens.Units;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * <b>第四个形态：常驻 HUD 面板</b>（A-24，2026-09-23）—— 一棵<b>只读</b>的 Trellis 树，
 * 每帧画在 HUD 上。
 *
 * <p>【它和另外三个形态的差别是"少了什么"】配置列 / 编辑场 / 网格都在 {@code Screen} 里，
 * 于是 {@code Screen} 白送了一整套前提：生命周期回调、resize、鼠标坐标、{@code tick} 的地方、
 * 键鼠事件的送达。这一屏<b>一样都没有</b>。所以本类回答的是三个具体问题：
 *
 * <ol>
 *   <li><b>谁 tick</b> —— 没有 {@code Screen.tick}，就由这条绘制回调自己喂
 *       （{@code tree.tick(now * 1_000_000L)}）。不喂的症状是"悬停硬切、没有缓动"，
 *       <b>而且不报错</b>（编辑场那边漏过，因为它的盒子无动画）。</li>
 *   <li><b>视口从哪来</b> —— 没有 resize 回调，每帧直接问 {@code gui.guiWidth()/guiHeight()}。</li>
 *   <li><b>没有输入会不会被框架强迫</b> —— 树上不调 {@code pointerMove} / {@code syncHover} /
 *       焦点，这一条是本轮最想验的（"不 tick 就不对"那类隐式要求，在这里会当场现形）。</li>
 * </ol>
 *
 * <p>【画布：它自己开一个 {@code NvgUi}】宿主已有先例（{@code NvgCardPainter} 的预热那几帧
 * 与主绘制在<b>同一个 MC 帧</b>里各开了一对 {@code begin/end}），而 {@code CardStage}
 * 那一帧是独立的 —— 所以本类不去改它，自己开一帧。
 *
 * <p>⚠️【"同帧两个 {@code NvgUi}"这条风险，本轮<b>没有</b>被验到 —— 是本类自己把它关掉的】
 * 立项时本喵把它列为最可能出事的一处（那条路带<b>延迟文字批次</b>：先登记、{@code close()}
 * 时才交给原版批次，而它从没被同帧开过两次）。评审查穿了：{@code NvgUi.begin} 全仓只有
 * 4 个调用点，三个在 {@code Screen.render} 里（一帧只渲染一个 Screen），第四个就是本类、
 * 而它的门是 {@code mc.screen == null} —— <b>于是每 MC 帧恒为 0 或 1 个 {@code NvgUi}，
 * 第二个在结构上不可能出现。</b>被点名"最可能出事"的那条，被这一轮自己写的那道门排除了，
 * <b>本轮对它零证据</b>。真要验，得让面板与某个 Screen 同帧共存（见下面那道门）。
 * 本轮真正验到的是另一件事：面板这一帧与宿主的 {@code NvgCanvas} 那一帧在同一 MC 帧里共存
 * 而没互相踩坏（真机第 67 轮，读数与几何逐条对上）。
 *
 * <p>【F1 / 没进世界 / 屏开着：三种情况都不画】
 * {@code hideGui} 是 F1；{@code level == null} 是主菜单与加载中（那时没有 HUD）；
 * {@code screen != null} 时也跳过。
 *
 * <p>⚠️【这道门比它的理由宽 —— 一条没写下来的决定（评审逮到）】原来的理由只说了
 * "那三个 Trellis 屏各自画满一整块背板"，而代码是<b>任何</b> Screen 都不画 ——
 * 于是按 E 开背包、开聊天、按 Esc 暂停，这条"常驻"面板<b>都会消失</b>。
 * 两条路留待拍板：① 收窄成"只有那三个屏才跳过"（但本层看不见平台层的屏类，
 * 要照 {@code CardStage.setSuspended} 的先例让屏自己报）；② 认下"只在游戏画面里出现"
 * 这个范围，把"常驻"改成实话。**本轮先认下 ②，并记进 {@code plan.md} 的遗留。**
 */
public final class HudStatusPanel {

    /** 全进程一个（同 {@code CardStage}）。 */
    public static final HudStatusPanel INSTANCE = new HudStatusPanel();

    /** 树与它的几何。只在 u 或底色变了时重建（同 {@code CardGridScreen} 的口径）。 */
    private HudStatusBar.Bar bar;

    /** 建树时的 u 与面板底色 —— 两者任一变了，这棵树就得重来。 */
    private float builtU = Float.NaN;
    private int builtFill;

    /** 上一帧树上的时刻（<b>纳秒</b>）—— 控件自检问的就是"这一帧画过没有"。 */
    private long lastNowNanos;

    /** 上一帧这条面板自己的耗时（微秒，含开合 NanoVG 那一帧）。 */
    private long lastMicros;

    private HudStatusPanel() {
    }

    @SubscribeEvent
    public void onHudRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.screen != null) {
            return;
        }
        GuiGraphics gui = event.getGuiGraphics();
        long now = System.currentTimeMillis();
        float u = Units.u(gui.guiHeight());
        NvgPalette palette = NvgPalette.of(CardStage.INSTANCE.previewStyle(), u);
        // 面板底色跟着 u 一起决定树要不要重来：底色是烘进 Box 的 Surface 里的，
        // 换了主题而树不重建的话，屏幕上会留着一块旧色 —— 那种错看得见但查起来很远。
        if (bar == null || u != builtU || palette.panel != builtFill) {
            String hint = I18n.get("pickupcard.hud.hint");
            // 提示盒的宽**量出来**，不从 token 猜 —— 猜的结果是字形缝把它缩成一团糊字
            // （见 `HudStatusBar.build` 的 `hintWidth` 那条：24px 装 41px 的字 = 缩到 58%）。
            // 用 `mc.font` 而不是等 `ui.font()`：树要在 `NvgUi` 会话**之前**建好，
            // 而两者取的是同一个字体（`NvgUi.begin` 里就是这一句）。
            // 文案与颜色一起交进树：字现在由 `Label` 自己画（从前是这句之后由宿主另画一笔）。
            bar = HudStatusBar.build("hud-count",
                    () -> I18n.get("pickupcard.hud.count", CardStage.INSTANCE.stats().live()),
                    () -> I18n.get("pickupcard.hud.hint"), palette.textDim, mc.font.width(hint),
                    gui.guiWidth(), gui.guiHeight(), u, palette);
            builtU = u;
            builtFill = palette.panel;
        }

        // 谁 tick —— 见类注释第 1 条。单位必须与 `PaintCtx.now` 一致（纳秒）。
        lastNowNanos = now * 1_000_000L;
        bar.tree().tick(lastNowNanos);
        HudStatusBar.layout(bar, gui.guiWidth(), gui.guiHeight(), 1f / guiScale());

        // 指针传 0：{@code NvgUi.mouseX/mouseY} 存下来但全仓<b>没有一处读它</b>
        // （本喵探过），而树是只读的、根本不吃指针。传 0 是诚实的，不是省事。
        long t0 = System.nanoTime();
        try (NvgUi ui = NvgUi.begin(gui, palette, 0f, 0f, now)) {
            if (ui != null) {
                TrellisColumn.Frame surface = TrellisColumn.surface(ui.canvas(), palette,
                        new McGlyphPainter(ui), guiScale());
                // ① 树：面板底 + 只读芯片 + 提示文字（提示那个 Label 自己经字形缝落笔，
                //    从前它的字在树外由宿主另画一笔 —— 那一笔已收进 Label）。
                TrellisColumn.paint(surface, bar.tree());
            }
        } finally {
            // 【为什么要计时】HUD 每帧都在游戏里跑，这里的预算是真的（三个 Screen 只在打开时跑）。
            // 而且这是**本 MC 帧里的第二个 NanoVG 帧** —— 它到底多贵，只有数出来才知道
            // （没验过的先例：{@code NvgUi} 那条带延迟文字批次的路从没被同帧开过两次）。
            // 用 finally：{@code ui == null}（NanoVG 不可用）那一支也要落数，否则读数会停在旧值上。
            lastMicros = (System.nanoTime() - t0) / 1_000L;
        }
    }

    /** GUI 倍数。设备像素对齐要用它（不交的话对齐会退化成"对齐到整数逻辑坐标"）。 */
    private float guiScale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    /**
     * 一行读数（给 harness）。<b>没画过就返回 null</b> —— 那与"画了 0 个"是两件事，
     * 读数里不能混。
     *
     * @param nowNanos 调用方<b>此刻</b>的时刻（纳秒）。这个参数不是装饰：
     *                 ⚠️ A-24 的评审逮到，第一版只报"上一次画的那一帧"，于是面板一旦画过，
     *                 读数就**永远**是 {@code 自检=1/1} —— 哪怕它因为开屏 / F1 / 出世界之后
     *                 再没画过。后果是 {@code DevHarness} 那条 {@code dump() == null} 的失败
     *                 检测**不可达**："面板消失了"这个最该被抓的故障，检测器恰恰抓不到。
     *                 <b>读数必须说得出自己是几帧前的</b>，所以帧龄要算进来。
     */
    public String dump(long nowNanos) {
        if (bar == null) {
            return null;
        }
        long ageMs = Math.max(0L, (nowNanos - lastNowNanos) / 1_000_000L);
        return bar.dump() + " 自检=" + bar.paintedCount(lastNowNanos) + "/1"
                + " 帧龄=" + ageMs + "ms 本帧=" + lastMicros + "us";
    }

    /** 注册到 Forge 总线（{@code PickupCard} 里与 {@code CardStage} 并排）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }
}
