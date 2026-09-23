package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.ui.widget.WidgetPalette;
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
 * **尺寸则相反，必须收敛**：它们是 {@code token × u}，A-14 起 u **每帧按画布高算**
 * （原来是烤死的 {@code static final U = Tokens.Unit.BASE}）—— 见 {@link #radius}。
 *
 * <p>【哪几个角色故意不用】`Color.BORDER_STRONG` / `TEXT_DISABLED` / `DANGER` / `FOCUS_RING`
 * 至今没有对应字段（分别是"强描边""禁用态""危险色""焦点环"还没做）。这一组对应关系由
 * {@code NvgPaletteRoleTest} 逐条钉着：**框架新增一个颜色角色时那条测试会红**，
 * 逼人在这里当场做一次决定，而不是让它悄悄漂着。
 */
public final class NvgPalette implements WidgetPalette {

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
     * "底色 + 一层白色叠加"，宿主用的是三个绝对色。后果不是"将来迁移时才要注意"——
     * 底色 alpha 只盖住七成左右，所以**改框架 `OVERLAY_HOVER / OVERLAY_PRESS` 的 alpha
     * 会直接改到玩家的观感**。也就是说"值全由本项目给"这条口径**今天只成立一半**。
     *
     * <p>【A-14 的更新】框架侧**已经有**"别画叠加层"的开关了
     * （{@code Component.stateOverlay(false)}，A-13 记的那条欠账已补）——
     * 但宿主**还没有打开它**（行内控件现在是"自己画 well 色 + 框架再叠一层"）。
     * 翻转它会改观感，所以留着等一次明确的决定；开关本身纯加法、不影响任何现有画面。
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
    /**
     * 焦点环 —— 角色：{@code Color.FOCUS_RING}。
     *
     * <p>【A-15 起它真的被画了】环的<b>画法与几何</b>归框架
     * （{@code Component.focusRing}：1 逻辑 px、贴盒子内缘、跟着组件自己的圆角）——
     * 和悬停叠加层同一个模式，"加一次、所有可聚焦组件都有"。宿主只给<b>值</b>。
     *
     * <p>【值为什么不直接用框架那个】框架的 {@code FOCUS_RING} 是给深色底配的亮青；
     * 宿主的浅色主题（{@link #of} 真会走到 {@link #light}）底是近白，同一个值读不出来。
     * 所以深色用框架的基准值、浅色用同一支青压暗（色相一致、对比够）。
     */
    public final int focusRing;
    /**
     * 圆钮的"亮面"（滑条被指/按住/聚焦时、开关处于开时）。
     *
     * <p>【为什么它进了调色板】它原来在 {@code Slider} 与 {@code Toggle} 里
     * <b>各写了一遍</b> {@code 0xFFFFFFFF}（A-13 评审列的"钮面颜色对"）——
     * 既不在 {@code NvgPalette} 也不在 {@code Tokens}，于是"角色跟着框架走"这条规矩
     * 管不到它，改一处就漏一处。**值一个字没改**（原来就是纯白）。
     *
     * <p>【角色：框架里没有对应 token】和 {@link #backdrop} 一样是宿主自己的角色
     * （框架的图层里没有"圆钮"这件事）—— 豁免登记在 {@code NvgPaletteRoleTest}。
     */
    public final int knobActive;
    /** 圆钮的"沉面"（滑条常态、开关处于关时）。角色同上，也是宿主自己的。值不变（原 {@code 0xFFD5DAE5}）。 */
    public final int knobIdle;

    /**
     * 控件圆角 = {@code Radius.MD} × u（A-14 起 u 每帧按画布高算，基准下 4）。
     *
     * <p>【为什么 u 是构造参数、这里不再有 {@code U = Tokens.Unit.BASE}】尺寸全是
     * {@code token × u}，而 u 是每帧按画布高算的（{@code Units.u(h)}）—— 把它写成
     * {@code static final} 会把基准尺寸烤进调色板，于是"行高跟着 u 缩、控件圆钮不缩"
     * （u=1.5 时 14px 的行里塞一个按基准画的钮）。**这是 A-14 漏掉的最后一处**：
     * 调色板在 {@code rebuild()} 里每次重建，u 现成可取。
     */
    public final float radius;
    /** 细线（描边）粗细 = {@code Size.HAIRLINE} —— **绝对 1px，不乘 u**（细线不该随屏幕放大）。 */
    public final float outlineWidth = Tokens.Size.HAIRLINE;
    /** 滑块（圆）半径 = {@code Size.KNOB_RADIUS} × u（A-14 起每帧算，基准下 5）。 */
    public final float knobRadius;
    /**
     * 细条（滑条轨道 / 屏幕滚动条）的厚度与圆角。
     *
     * <p>【为什么是绝对 px、不乘 u】和 {@code Size.HAIRLINE} 同一个理由：一条细线看起来
     * 该多细是"看得清"的事，屏幕大一号它不该跟着变粗（那就不像细条、像边框了）。
     *
     * <p>【为什么进调色板】A-13 评审列的"细条 3u + 圆角 1.5u"：{@code Slider} 的轨道与
     * 屏幕里的滚动条**各写了一遍** {@code 3f / 1.5f}。它们是同一个角色（"一条细的圆头条"），
     * 收在这里一处。**值一个字没改**。
     */
    public float trackThickness = 3f;
    /** 细条的圆角（= 厚度的一半，胶囊端）。见 {@link #trackThickness}。 */
    public float trackRadius = 1.5f;
    /**
     * 这一帧的自适应单位 u（A-28）—— 控件内部按 {@code Tokens.Space} 阶梯取间距时乘它。
     *
     * <p>【为什么要留着它、不只用 radius/knobRadius】那两个是"点过名的角色"，值已经乘过 u；
     * 而控件内部还有一批<b>没被点名</b>的间距（离盒边留多少、元素之间的缝），数量多且各家不同。
     * 与其把 {@code WidgetPalette} 撑成几十项，不如把 u 交出去让控件乘**同一个阶梯** ——
     * 统一性来自"控件挑的都是那几个档"，不是来自角色清单的长度。与 radius 同源（同一个入参）。
     */
    public final float u;
    // 【删掉的两个字段（2026-09-22）】`rowHeight = 18f` 与 `trackHeight = 6f` —— **全仓没有任何读点**：
    // 行高由 `ConfigRows` / 组件树用 `Size.ROW_H × u` 给；轨道高由 `trackThickness` 给。
    // 留着这两个死字段只会让人以为"改这里能调行高/轨道高"，而改了什么都不发生。

    /** 深色界面。默认就是它 —— 游戏里九成时间在暗环境，浅色面板会晃眼。 */
    public static NvgPalette dark(StyleModel.Accents a, float u) {
        return new NvgPalette(0xF0101218, 0xC0202836, 0x80202836, 0xB0364152, 0xC04A5871,
                a.xp(), 0x40FFFFFF, 0xFFEBEFF6, 0xFF9AA4AD, 0xFFFFFFFF, 0xFFD5DAE5,
                Tokens.Color.FOCUS_RING, u);
    }

    /** 浅色：跟着主题走（主题是浅色时用这套）。 */
    public static NvgPalette light(StyleModel.Accents a, float u) {
        // 焦点环压暗：同一个青（色相不变）、亮度降到近白底上读得出来。
        return new NvgPalette(0xF0E9ECF3, 0xC0FFFFFF, 0x60D5DAE5, 0xA0C3CAD8, 0xC0A9B2C4,
                a.xp(), 0x40000000, 0xFF1B1F27, 0xFF5A6272, 0xFFFFFFFF, 0xFFD5DAE5,
                0xCC0E7C8C, u);
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
    public static NvgPalette of(StyleModel style, float u) {
        // 卡面底色偏亮（fillTop 的 alpha/亮度高）就当作浅色主题
        int rgb = style.fillTop() & 0xFFFFFF;
        int brightness = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
        StyleModel.Accents accents = style.accents();
        return brightness > 3 * 128 ? light(accents, u) : dark(accents, u);
    }

    private NvgPalette(int backdrop, int panel, int well, int wellHover, int wellPressed,
                       int accent, int outline, int text, int textDim,
                       int knobActive, int knobIdle, int focusRing, float u) {
        this.backdrop = backdrop;
        this.panel = panel;
        this.well = well;
        this.wellHover = wellHover;
        this.wellPressed = wellPressed;
        this.accent = accent;
        this.outline = outline;
        this.text = text;
        this.textDim = textDim;
        this.knobActive = knobActive;
        this.knobIdle = knobIdle;
        this.focusRing = focusRing;
        // 尺寸：token × u。u 由调用方按画布高算好传进来（见 radius 的 javadoc）。
        this.u = u;
        this.radius = Tokens.Radius.MD * u;
        this.knobRadius = Tokens.Size.KNOB_RADIUS * u;
    }

    // ------------------------------------------------------------------
    // WidgetPalette：控件只认这 15 个角色（A-18）
    // ------------------------------------------------------------------

    /**
     * 【为什么这里只是"把字段读出来"】角色名与字段名一一对应是有意的：<b>值仍然是这一份</b>，
     * 框架拿到的只是一个受约束的视图（它只认这 15 个名字，别的配色它碰不到）。
     * 接口把"控件需要什么"写成了可编译的清单 —— 少一个角色，编译就红，而不是运行期读到 0。
     */
    @Override
    public int well() {
        return well;
    }

    @Override
    public int wellHover() {
        return wellHover;
    }

    @Override
    public int wellPressed() {
        return wellPressed;
    }

    @Override
    public int panel() {
        return panel;
    }

    @Override
    public int accent() {
        return accent;
    }

    @Override
    public int outline() {
        return outline;
    }

    @Override
    public int text() {
        return text;
    }

    @Override
    public int textDim() {
        return textDim;
    }

    @Override
    public int knobActive() {
        return knobActive;
    }

    @Override
    public int knobIdle() {
        return knobIdle;
    }

    /**
     * 焦点环的颜色 —— 角色 {@code Color.FOCUS_RING}。
     *
     * <p>【为什么 A-23 才补进接口】它一直是这个类的字段（A-15 的焦点环在用），但框架那侧的
     * 角色清单里没有它 —— 于是 {@code WidgetSlot} 只能直接读宿主这一份。那个类搬进框架之后
     * 这条路断了（框架不认识 {@code NvgPalette}），所以补成角色。<b>值一个字没改。</b>
     */
    @Override
    public int focusRing() {
        return focusRing;
    }

    @Override
    public float radius() {
        return radius;
    }

    @Override
    public float outlineWidth() {
        return outlineWidth;
    }

    @Override
    public float knobRadius() {
        return knobRadius;
    }

    @Override
    public float trackThickness() {
        return trackThickness;
    }

    @Override
    public float trackRadius() {
        return trackRadius;
    }

    /**
     * 这一帧的自适应单位 u —— 控件内部按 {@code Tokens} 阶梯取间距时乘它（A-28）。
     *
     * <p>【为什么不直接给一大堆具名间距】控件要的间距是"离盒边多少、元素之间多少"这类，
     * 数量多且各家不同；给角色清单会把它撑成几十项。交一个 {@code u} 出去，让控件乘
     * {@code Tokens.Space} 的<b>同一个阶梯</b>，统一性才在（控件挑的都是那几个档）。
     * 这是本对象构造时收进来的那个 u，与 {@code radius} / {@code knobRadius} 同源。
     */
    @Override
    public float u() {
        return u;
    }
}
