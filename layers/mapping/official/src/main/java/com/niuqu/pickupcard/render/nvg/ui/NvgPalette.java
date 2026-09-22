package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.tokens.Tokens;

/**
 * 自绘界面的配色与尺寸 —— <b>一个地方改，所有控件跟着变</b>。
 *
 * <p>【为什么单独成类】旧版（v0.1.0）就有一份 {@code UiPalette}，颜色散在六个控件文件里。
 * 散着写的代价不是"难改"，是<b>改不干净</b>：调一次底色，六个控件的悬停色就不一致了，
 * 而那种不一致眼睛看得出来、名字叫不出来。
 *
 * <p>【颜色为什么是 ARGB int 而不是 NVGColor】{@code NVGColor} 要挂在 MemoryStack 上、
 * 有生命周期；控件只在画的那一刻才需要它。存 int，画的瞬间再转 —— 这样调色板是纯数据，
 * 也就不用担心"这一帧的 MemoryStack 已经弹掉了"。
 *
 * <p>【两套值、一套角色（2026-09-22 定的口径）】下面每个字段都对应 Trellis L0 的一个
 * **颜色角色**（{@code Tokens.Color.*}，真源 {@code design/tokens.css}），而**值仍由本项目给** ——
 * 这是已发布外观（v0.1.0 起就这样、{@code main} 上也有），直接换成框架那份值等于悄悄改了
 * 玩家看到的东西。所以要收敛的是**语言**（角色名与层级），不是色号。
 * **尺寸则相反，必须收敛**：它们是 token × u，今天 u 取基准所以数值不变。
 *
 * <p>【哪几个角色故意不用】`Color.BORDER_STRONG` / `TEXT_DISABLED` / `DANGER` / `FOCUS_RING`
 * 至今没有对应字段（分别是"强描边""禁用态""危险色""焦点环"还没做）。这一组对应关系由
 * {@code NvgPaletteRoleTest} 逐条钉着：**框架新增一个颜色角色时那条测试会红**，
 * 逼人在这里当场做一次决定，而不是让它悄悄漂着。
 */
public final class NvgPalette {

    /** 界面底色（不透明，压在游戏画面上）。 */
    /**
     * 界面底色（不透明，压在游戏画面上）—— **没有对应的 Trellis 角色**：
     * 框架不知道"压在游戏画面上"这件事（见 {@code NvgPaletteRoleTest} 的对应表）。
     */
    public final int backdrop;
    /** 面板/侧栏底 —— 角色：{@code Color.SURFACE}。 */
    public final int panel;
    /** 控件底（未交互）—— 角色：{@code Color.SURFACE_RAISED}（凸起在面板上的一格）。 */
    public final int well;
    /**
     * 控件底（悬停）—— 角色：{@code Color.OVERLAY_HOVER}。
     *
     * <p>⚠️ **角色对得上、机制不同，而且框架那一层现在就已经叠在这下面**：框架的表达是
     * "底色 + 一层白色叠加"（`Component.draw` 无条件画），宿主用的是三个绝对色。后果不是"将来迁移时
     * 才要注意"—— 底色 alpha 只盖住七成左右，所以**改框架 `OVERLAY_HOVER / OVERLAY_PRESS` 的 alpha
     * 会直接改到玩家的观感**。也就是说"值全由本项目给"这条口径**今天只成立一半**。
     * 框架侧目前没有"别画叠加层"的开关（`Component.draw` 是 final 且无条件）—— 这是记在案的真欠账。
     */
    public final int wellHover;
    /** 控件底（按下）—— 角色：{@code Color.OVERLAY_PRESS}。机制差别同 {@link #wellHover}。 */
    public final int wellPressed;
    /**
     * 填充条 / 选中态 —— 角色：{@code Color.ACCENT}。
     *
     * <p>**它的值是数据驱动的**（按稀有度从卡面主题取，见 {@link #of}），所以它永远不会去读 token ——
     * "强调色"这个**角色**归框架，"这一张卡用什么色"归内容。
     */
    public final int accent;
    /** 描边 —— 角色：{@code Color.BORDER}。 */
    public final int outline;
    /** 正文 —— 角色：{@code Color.TEXT_PRIMARY}。 */
    public final int text;
    /** 次要文字（标签、说明）—— 角色：{@code Color.TEXT_SECONDARY}。 */
    public final int textDim;

    // ---- 尺寸（token × u；u 今天取基准，见 {@link Tokens.Unit}）----
    private static final float U = Tokens.Unit.BASE;
    /** 控件圆角 = {@code Radius.MD} × u（基准下 4）。 */
    public float radius = Tokens.Radius.MD * U;
    /** 细线（描边）粗细 = {@code Size.HAIRLINE} —— **绝对 1px，不乘 u**（细线不该随屏幕放大）。 */
    public float outlineWidth = Tokens.Size.HAIRLINE;
    /** 滑块（圆）半径 = {@code Size.KNOB_RADIUS} × u（基准下 5）。 */
    public float knobRadius = Tokens.Size.KNOB_RADIUS * U;
    // 【删掉的两个字段（2026-09-22）】`rowHeight = 18f` 与 `trackHeight = 6f` —— **全仓没有任何读点**：
    // 行高由 `ConfigRows` / 组件树用 `Size.ROW_H × u` 给；轨道高**还没有 token**（`NvgSlider` 里那句
    // 细条 3u + 圆角 1.5u，与屏幕里滚动条那一对是同一份裸数字，两处各写一遍 —— 记在下一片）。
    // 留着这两个死字段只会让人以为"改这里能调行高/轨道高"，而改了什么都不发生。

    /** 深色界面。默认就是它 —— 游戏里九成时间在暗环境，浅色面板会晃眼。 */
    public static NvgPalette dark(StyleModel.Accents a) {
        return new NvgPalette(0xF0101218, 0xC0202836, 0x80202836, 0xB0364152, 0xC04A5871,
                a.xp(), 0x40FFFFFF, 0xFFEBEFF6, 0xFF9AA4AD);
    }

    /** 浅色：跟着主题走（主题是浅色时用这套）。 */
    public static NvgPalette light(StyleModel.Accents a) {
        return new NvgPalette(0xF0E9ECF3, 0xC0FFFFFF, 0x60D5DAE5, 0xA0C3CAD8, 0xC0A9B2C4,
                a.xp(), 0x40000000, 0xFF1B1F27, 0xFF5A6272);
    }

    /**
     * 按卡面主题选一套界面配色 —— 界面跟卡面同族，不然像两个 mod 拼在一起。
     * <p>【强调色为什么也从主题取】它原本写死成 {@code 0xFF7DFF8A / 0xFF2E9E45}，
     * 而那两个数<b>正好就是主题里 {@code accent.xp} 的值</b> —— 又一处"一份真源两份数据"：
     * 玩家把主题的 xp 色改掉之后，卡片的经验条跟着变、界面上的选中色却不动。
     * <p>【其余几色为什么留在界面自己这儿】它们不是任何卡片颜色的副本，只有这一份
     * （见类注释：旧版正是"颜色散在六个控件文件里"才改不干净的）。让界面 chrome 也能
     * 整套换肤是另一个决定，不属于"清理重复"。
     */
    public static NvgPalette of(StyleModel style) {
        // 卡面底色偏亮（fillTop 的 alpha/亮度高）就当作浅色主题
        int rgb = style.fillTop() & 0xFFFFFF;
        int brightness = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
        StyleModel.Accents accents = style.accents();
        return brightness > 3 * 128 ? light(accents) : dark(accents);
    }

    private NvgPalette(int backdrop, int panel, int well, int wellHover, int wellPressed,
                       int accent, int outline, int text, int textDim) {
        this.backdrop = backdrop;
        this.panel = panel;
        this.well = well;
        this.wellHover = wellHover;
        this.wellPressed = wellPressed;
        this.accent = accent;
        this.outline = outline;
        this.text = text;
        this.textDim = textDim;
    }
}
