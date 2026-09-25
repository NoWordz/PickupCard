package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.FullPolicy;
import com.niuqu.pickupcard.notice.MergeMode;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.render.CardStage;
import dev.e33.trellis.ui.widget.Button;
import dev.e33.trellis.ui.widget.ColorChip;
import dev.e33.trellis.ui.widget.Slider;
import dev.e33.trellis.ui.widget.Toggle;
import dev.e33.trellis.ui.widget.Widget;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.text.CountFormat;
import com.niuqu.pickupcard.text.CountMode;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 配置项注册表：每个配置项一行声明 —— <b>归哪一页、哪个小节、控件怎么造、默认值是什么</b>。
 *
 * <p>【为什么必须有这一个类（2026-09-19 架构审计定案）】用户报的四个"功能打架"里有三个
 * 出在"同一件事的真相存了好几处"：
 * <ol>
 *   <li><b>页面归属</b>从前写在五个 buildX 方法里，漂了：入场的「展开方式」落在位置页，
 *       玩家在动画页找不到它（第一问）。现在归谁全在这一张表里。</li>
 *   <li><b>默认值</b>从前在「恢复本页默认」里手抄了三十多个字面量，漂了：exitMode 抄的是
 *       FADE，正本已改成 TRAIN —— 点"恢复默认"反而改掉设置（第四问）。现在恢复一律
 *       {@code value.set(value.getDefault())}：默认值只有一个出处，就是 TOML 键定义自己。</li>
 *   <li>行是<b>纯数据</b>（控件供给器是懒的），页面归属与恢复值因此第一次能离线单测
 *       （{@code ConfigPageSpecTest}）。</li>
 * </ol>
 *
 * <p>【界面自己不存值】控件的读写直接打到 Forge 配置上（"界面显示 5、实际画 9"的老坑），
 * 这条纪律原样搬过来：每个工厂最后一行都是 {@link #changed()}。
 */
public final class ConfigPageSpec {

    /** 配置界面的一页。标签与页说明（底部那行）也归这里 —— 从前在界面的 Section 枚举里。 */
    public enum Page {
        GENERAL("pickupcard.config.page.general.name", "pickupcard.config.page.general.hint"),
        ANIM("pickupcard.config.page.anim.name", "pickupcard.config.page.anim.hint"),
        LAYOUT("pickupcard.config.page.layout.name", "pickupcard.config.page.layout.hint"),
        LOOK("pickupcard.config.page.look.name", "pickupcard.config.page.look.hint"),
        /** 【为什么单独一页】三张名单是可增删的列表，行数会涨，跟固定八行的"外观"不是一回事。 */
        FILTER("pickupcard.config.page.filter.name", "pickupcard.config.page.filter.hint");

        // 【存 key 不存文案】枚举常量在类加载时初始化，那时语言可能没加载完/还会切换 ——
        // 文案在 label()/hint() 被调用的那一刻解析。测试环境里 I18n.get 原样返回 key，
        // 测试因此钉的是"接线"而不是措辞（措辞归语言文件 + 一致性单测）。
        private final String labelKey;
        private final String hintKey;

        Page(String labelKey, String hintKey) {
            this.labelKey = labelKey;
            this.hintKey = hintKey;
        }

        public String label() {
            return I18n.get(labelKey);
        }

        public String hint() {
            return I18n.get(hintKey);
        }
    }

    /**
     * 一行。{@code widget == null} 的是小节头（占同样的行距、画暗色小字、不接悬停）。
     * <p>【供给器为什么是懒的】{@link #rows} 在测试里也会被调用，此时 Forge 配置还没加载、
     * Minecraft 还没启动 —— 控件只有在游戏里重建界面时才真正构造。
     */
    public record Row(Page page, String label, String hint,
                      Supplier<Widget> widget, Runnable restore) {

        public boolean isHeader() {
            return widget == null;
        }

        public boolean restorable() {
            return restore != null;
        }
    }

    /** 「位置」那行要回调界面两件事：显示当前值、打开编辑场。界面自己实现。 */
    public interface AnchorBridge {

        /** 「位置」钮上的一句话（自动还是自定义、自定义在哪儿）。 */
        String anchorValueText();

        /** 点开整屏拖拽编辑场。 */
        void openEditor();
    }

    private ConfigPageSpec() {
    }

    /** 文案一律走语言文件（zh_cn/en_us）；测试环境里 I18n 原样返回 key —— 测试钉接线不钉措辞。 */
    private static String tr(String key) {
        return I18n.get(key);
    }

    /**
     * 全部配置行。顺序 = 玩家看到的顺序（页内小节头夹在中间）。
     *
     * @param style  当前生效的样式（主题 + 玩家改动）—— 外观/动画行的"生效值"显示用
     * @param eff    当前生效的行为设置 —— 行为行的"生效值"显示用
     * @param bridge 「位置」行的回调（只被那一行的控件供给器捕获，不会立刻调用）
     */
    public static List<Row> rows(StyleModel style, PickupCardSettings eff, AnchorBridge bridge) {
        List<Row> rows = new ArrayList<>();
        PickupCardConfig.Values v = PickupCardConfig.VALUES;
        addGeneral(rows, v, eff);
        addAnim(rows, v, style, eff);
        addLayout(rows, v, eff, bridge);
        addLook(rows, v, style);
        return List.copyOf(rows);
    }

    // ------------------------------------------------------------------
    // 通用：总开关置顶，按「显示什么」「行为与合并」分两节
    // ------------------------------------------------------------------

    private static void addGeneral(List<Row> rows, PickupCardConfig.Values v, PickupCardSettings eff) {
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.master.name"),
                tr("pickupcard.config.row.master.hint"),
                () -> bool(v.enabled, eff.enabled()), restore(v.enabled)));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.section.show"), null, null, null));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.showName.name"),
                tr("pickupcard.config.row.showName.hint"),
                () -> bool(v.showItemName, eff.showItemName()), restore(v.showItemName)));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.showId.name"),
                tr("pickupcard.config.row.showId.hint"),
                () -> bool(v.showItemId, eff.showItemId()), restore(v.showItemId)));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.nameMaxWidth.name"),
                tr("pickupcard.config.row.nameMaxWidth.hint"),
                () -> number(v.nameMaxWidth, eff.nameMaxWidth(), 0, 400, 1, "px"),
                restore(v.nameMaxWidth)));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.countFormat.name"),
                tr("pickupcard.config.row.countFormat.hint"),
                () -> cycle(v.countFormat, CountFormat.values(), ConfigPageSpec::countName),
                restore(v.countFormat)));
        // 数字含义：0.2.3 的新选项，紧跟「数量写法」—— 写法管"长什么样"，含义管"数的是哪笔账"，
        // 玩家看到数字不对时这两个设置要一起看
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.countMode.name"),
                tr("pickupcard.config.row.countMode.hint"),
                () -> cycle(v.countMode, CountMode.values(), ConfigPageSpec::countModeName),
                restore(v.countMode)));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.section.behavior"), null, null, null));
        // 间距没有"-1=没改"的哨兵（范围 0..32），shown 永远不会用到 —— 传默认值即可
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.separation.name"),
                tr("pickupcard.config.row.separation.hint"),
                () -> decimal(v.separation, LayoutSettings.DEFAULT_SEPARATION, 0, 16),
                restore(v.separation)));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.mergeMode.name"),
                tr("pickupcard.config.row.mergeMode.hint"),
                () -> cycle(v.mergeMode, MergeMode.values(), ConfigPageSpec::mergeName),
                restore(v.mergeMode)));
        // 磁铁检测：0.2.3 的新选项，放「行为与合并」节尾 —— 它改的也是"什么算我得到的东西"
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.magnet.name"),
                tr("pickupcard.config.row.magnet.hint"),
                () -> bool(v.magnetEnabled, eff.magnetEnabled()), restore(v.magnetEnabled)));
        rows.add(new Row(Page.GENERAL, tr("pickupcard.config.row.magnetRadius.name"),
                tr("pickupcard.config.row.magnetRadius.hint"),
                () -> radius(v.magnetRadius, 1, 16),
                restore(v.magnetRadius)));
    }

    // ------------------------------------------------------------------
    // 动画：入场（含展开方式）/ 停留与消失 / 合并与跳动
    // ------------------------------------------------------------------

    private static void addAnim(List<Row> rows, PickupCardConfig.Values v,
                                StyleModel style, PickupCardSettings eff) {
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.section.enter"), null, null, null));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.enterMs.name"),
                tr("pickupcard.config.row.enterMs.hint"),
                () -> styleTime(v.stEnterMs, style.enterMs(), 0, 2_000, 40), restore(v.stEnterMs)));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.enterToggle.name"),
                tr("pickupcard.config.row.enterToggle.hint"),
                () -> styleSwitch(v.stEnterEnabled, style.enterEnabled()),
                restore(v.stEnterEnabled)));
        // 【2026-09-19 搬家】展开方式 = 入场的形态（火车/拉幕），从前错放在位置页 ——
        // 玩家在动画页找不到它（审计第一问）。它跟入场时长是一伙的。
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.appearMode.name"),
                tr("pickupcard.config.row.appearMode.hint"),
                () -> cycle(v.appearMode, LayoutSettings.Appear.values(), ConfigPageSpec::appearName),
                restore(v.appearMode)));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.section.holdExit"), null, null, null));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.holdMs.name"),
                tr("pickupcard.config.row.holdMs.hint"),
                () -> time(v.holdMs, eff.holdMs(), 500, 10_000, 250), restore(v.holdMs)));
        // 停留摇摆：0.2.3 的新选项，默认关 —— 只加选项不改默认观感（LayoutSettings.defaults 同款纪律）。
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.sway.name"),
                tr("pickupcard.config.row.sway.hint"),
                () -> bool(v.swayEnabled, PickupCardConfig.layoutSnapshot().swayEnabled()),
                restore(v.swayEnabled)));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.exitMode.name"),
                tr("pickupcard.config.row.exitMode.hint"),
                () -> cycle(v.exitMode, LayoutSettings.Exit.values(), ConfigPageSpec::exitName),
                restore(v.exitMode)));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.exitMs.name"),
                tr("pickupcard.config.row.exitMs.hint"),
                () -> time(v.exitMs, eff.exitMs(), 0, 2_000, 20), restore(v.exitMs)));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.section.bump"), null, null, null));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.bumpToggle.name"),
                tr("pickupcard.config.row.bumpToggle.hint"),
                () -> styleSwitch(v.stBumpEnabled, style.bumpEnabled()), restore(v.stBumpEnabled)));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.bumpMs.name"),
                tr("pickupcard.config.row.bumpMs.hint"),
                () -> styleTime(v.stBumpMs, style.bumpMs(), 0, 1_000, 20), restore(v.stBumpMs)));
        rows.add(new Row(Page.ANIM, tr("pickupcard.config.row.reviveMs.name"),
                tr("pickupcard.config.row.reviveMs.hint"),
                () -> styleTime(v.stReviveMs, style.reviveMs(), 0, 1_000, 20), restore(v.stReviveMs)));
    }

    // ------------------------------------------------------------------
    // 位置与堆叠：锚在哪（拖拽调整）、怎么对齐、同屏几张
    // ------------------------------------------------------------------

    private static void addLayout(List<Row> rows, PickupCardConfig.Values v,
                                  PickupCardSettings eff, AnchorBridge bridge) {
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.section.position"), null, null, null));
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.row.position.name"),
                tr("pickupcard.config.row.position.hint"),
                () -> new Button("", bridge::anchorValueText, bridge::openEditor), null));
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.row.align.name"),
                tr("pickupcard.config.row.align.hint"),
                () -> cycle(v.align, LayoutSettings.Side.values(), ConfigPageSpec::sideName),
                restore(v.align)));
        // 镜像卡片：竖条搬到最右、内容左右反转、动画方向跟着翻（2026-09-20 用户新增）
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.row.mirror.name"),
                tr("pickupcard.config.row.mirror.hint"),
                () -> bool(v.mirrorCard, PickupCardConfig.layoutSnapshot().mirrorCard()),
                restore(v.mirrorCard)));
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.row.scale.name"),
                tr("pickupcard.config.row.scale.hint"),
                () -> percent(v.scalePercent, PickupCardConfig.layoutSnapshot().scalePercent()),
                restore(v.scalePercent)));
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.section.count"), null, null, null));
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.row.maxOnScreen.name"),
                tr("pickupcard.config.row.maxOnScreen.hint"),
                () -> number(v.maxOnScreen, eff.maxOnScreen(), 1, 16, 1, tr("pickupcard.config.unit.cards")),
                restore(v.maxOnScreen)));
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.row.queueSize.name"),
                tr("pickupcard.config.row.queueSize.hint"),
                () -> number(v.queueSize, eff.queueSize(), 0, 32, 1, tr("pickupcard.config.unit.cards")),
                restore(v.queueSize)));
        // 屏满策略：REPLACE = 新卡顶掉最老的旧卡立刻上屏（2026-09-20 定案的新默认）；
        // QUEUE = 旧行为，「排队上限」「溢出卡」只在这一档才用得上，所以紧跟在它们后面。
        rows.add(new Row(Page.LAYOUT, tr("pickupcard.config.row.fullPolicy.name"),
                tr("pickupcard.config.row.fullPolicy.hint"),
                () -> cycle(v.fullPolicy, FullPolicy.values(), ConfigPageSpec::fullPolicyName),
                restore(v.fullPolicy)));
    }

    // ------------------------------------------------------------------
    // 外观：形状一节、颜色一节（全部"-1/空串 = 跟随主题"）
    // ------------------------------------------------------------------

    private static void addLook(List<Row> rows, PickupCardConfig.Values v, StyleModel style) {
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.section.shape"), null, null, null));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.barWidth.name"),
                tr("pickupcard.config.row.barWidth.hint"),
                () -> styleNumber(v.stBarWidth, style.barWidth(), 1, 8, 1, "px"),
                restore(v.stBarWidth)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.paddingV.name"),
                tr("pickupcard.config.row.paddingV.hint"),
                () -> styleNumber(v.stPaddingV, style.paddingV(), 0, 8, 1, ""),
                restore(v.stPaddingV)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.iconSize.name"),
                tr("pickupcard.config.row.iconSize.hint"),
                () -> styleNumber(v.stIconSize, style.iconSize(), 8, 64, 1, "px"),
                restore(v.stIconSize)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.cornerRadius.name"),
                tr("pickupcard.config.row.cornerRadius.hint"),
                () -> styleNumber(v.stCornerRadius, style.cornerRadius(), 0, 16, 1, "px"),
                restore(v.stCornerRadius)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.borderWidth.name"),
                tr("pickupcard.config.row.borderWidth.hint"),
                () -> styleNumber(v.stBorderWidth, style.borderWidth(), 0, 4, 1, "px"),
                restore(v.stBorderWidth)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.section.color"), null, null, null));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.fillTop.name"),
                tr("pickupcard.config.row.fillTop.hint"),
                () -> color(v.stFillTop, style.fillTop()), restore(v.stFillTop)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.fillBottom.name"),
                tr("pickupcard.config.row.fillBottom.hint"),
                () -> color(v.stFillBottom, style.fillBottom()), restore(v.stFillBottom)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.borderColor.name"),
                tr("pickupcard.config.row.borderColor.hint"),
                () -> color(v.stBorder, style.border()), restore(v.stBorder)));
        rows.add(new Row(Page.LOOK, tr("pickupcard.config.row.nameColor.name"),
                tr("pickupcard.config.row.nameColor.hint"),
                () -> color(v.stNameColor, style.nameColor()), restore(v.stNameColor)));
    }

    // ------------------------------------------------------------------
    // 恢复默认：默认值只有一个出处 —— TOML 键定义自己
    // ------------------------------------------------------------------

    /** 「恢复本页默认」那行的说明：项数现算，杜绝"九项"那种手抄漂移。 */
    public static String restoreHint(Page page, int restorable) {
        if (page == Page.FILTER) {
            return tr("pickupcard.config.restore.filter");
        }
        String base = I18n.get("pickupcard.config.restore.count", restorable);
        if (page == Page.LAYOUT) {
            return base + tr("pickupcard.config.restore.anchorNote");
        }
        return base;
    }

    /** 恢复 = 写回键定义时的默认值。手抄字面量的那条路（漂出过 FADE）到此封死。 */
    private static <T> Runnable restore(ForgeConfigSpec.ConfigValue<T> value) {
        return () -> value.set(value.getDefault());
    }

    // ------------------------------------------------------------------
    // 控件工厂：配置读写都从这里进出（原样搬自界面，静态化）
    // ------------------------------------------------------------------

    static void changed() {
        CardStage.INSTANCE.refreshStyle();
    }

    private static Toggle bool(ForgeConfigSpec.BooleanValue config, boolean shown) {
        return new Toggle("", config::get, on -> {
            config.set(on);
            changed();
        }, () -> I18n.get("pickupcard.config.toggle.on"), () -> I18n.get("pickupcard.config.toggle.off"));
    }

    /** 外观开关：配置里是 -1/0/1，界面只显示"开/关"，点了就写死 1/0。 */
    private static Toggle styleSwitch(ForgeConfigSpec.IntValue config, boolean shown) {
        return new Toggle("", () -> config.get() < 0 ? shown : config.get() == 1, on -> {
            config.set(on ? 1 : 0);
            changed();
        }, () -> I18n.get("pickupcard.config.toggle.on"), () -> I18n.get("pickupcard.config.toggle.off"));
    }

    private static Slider number(ForgeConfigSpec.IntValue config, int shown, int min, int max, int step,
                                    String suffix) {
        return new Slider("", min, max, step,
                () -> (double) currentInt(config, shown), value -> {
            config.set((int) Math.round(value));
            changed();
        },
                value -> Integer.toString((int) Math.round(value)) + suffix);
    }

    private static Slider styleNumber(ForgeConfigSpec.IntValue config, int shown, int min, int max,
                                         int step, String suffix) {
        return number(config, shown, min, max, step, suffix);
    }

    private static Slider decimal(ForgeConfigSpec.DoubleValue config, double shown, double min, double max) {
        return new Slider("", min, max, 1,
                () -> currentDouble(config, shown),
                value -> {
                    config.set((double) Math.round(value));
                    changed();
                },
                value -> Math.round(value) + "px");
    }

    /**
     * 磁铁半径（格）：半格步进 —— 单位是方块不是像素，4.5 格是有意义的档；
     * 与 {@link #decimal}（整数像素 + "px" 后缀）分两处，谁也别挤谁。
     * <p>【没有 -1 哨兵】半径不存在"跟随主题/自动"的第三态，{@code shown} 因此不传 ——
     * 与 separation 那行实际行为一致（它的 {@code shown} 也永远用不到）。
     */
    private static Slider radius(ForgeConfigSpec.DoubleValue config, double min, double max) {
        return new Slider("", min, max, 0.5,
                () -> config.get(),
                value -> {
                    config.set(Math.round(value * 2) / 2.0);
                    changed();
                },
                value -> Math.round(value * 2) / 2.0 + tr("pickupcard.config.unit.blocks"));
    }

    /**
     * 时间滑条：底层一律 ms（TOML 兼容），<b>显示一律秒</b>（2026-09-25 用户反馈：
     * 玩家对 2600ms 没有直觉）。两位小数同时照顾 holdMs（3.20s）与 exitMs（0.32s）。
     */
    private static Slider time(ForgeConfigSpec.LongValue config, long shown, long min, long max, long step) {
        return new Slider("", min, max, step,
                () -> (double) currentLong(config, shown), value -> {
            config.set(Math.round(value));
            changed();
        },
                value -> String.format(java.util.Locale.ROOT, "%.2fs", value / 1000.0));
    }

    private static Slider styleTime(ForgeConfigSpec.LongValue config, long shown, long min, long max, long step) {
        return time(config, shown, min, max, step);
    }

    /**
     * 卡片缩放：0 是个真值（「自动」）。
     * <p>滑条上 0 是"自动"，1..49 是空档 —— 不夹的话界面会显示 "10%" 而生效的是 50%
     * （sanitized 会夹），那就是"设了等于没设"。
     */
    private static Slider percent(ForgeConfigSpec.IntValue config, int shown) {
        return new Slider("", LayoutSettings.AUTO_SCALE, LayoutSettings.MAX_SCALE_PERCENT, 5,
                () -> (double) config.get(),
                value -> {
                    int pct = (int) Math.round(value);
                    config.set(pct <= LayoutSettings.AUTO_SCALE ? LayoutSettings.AUTO_SCALE
                            : Math.max(LayoutSettings.MIN_SCALE_PERCENT, pct));
                    changed();
                },
                value -> value <= LayoutSettings.AUTO_SCALE ? tr("pickupcard.config.value.auto") : Math.round(value) + "%");
    }

    private static <E extends Enum<E>> Button cycle(ForgeConfigSpec.EnumValue<E> config, E[] values,
                                                       Function<E, String> name) {
        return new Button("", () -> name.apply(config.get()), () -> {
            E current = config.get();
            int i = 0;
            for (int k = 0; k < values.length; k++) {
                if (values[k] == current) {
                    i = k;
                }
            }
            config.set(values[(i + 1) % values.length]);
            changed();
        });
    }

    /** 颜色 = 色块循环；精确色值的出路在 TOML，行说明里有写。 */
    private static ColorChip color(ForgeConfigSpec.ConfigValue<String> config, int effectiveArgb) {
        return new ColorChip("", () -> effectiveArgb(config, effectiveArgb), config::get, text -> {
            config.set(text);
            changed();
        }, () -> I18n.get("pickupcard.config.value.color.theme"),
                () -> I18n.get("pickupcard.config.value.color.invalid"));
    }

    /** 色块画的生效色：配置里解析得动就用解析值（含手写 hex），否则回主题生效值。 */
    private static int effectiveArgb(ForgeConfigSpec.ConfigValue<String> config, int effective) {
        return StyleOverrides.parseArgb(config.get()).orElseGet(() -> effective);
    }

    /** 显示用的当前值：配置里"没改过"（-1）时给生效值，界面因此永远不显示第三态。 */
    private static int currentInt(ForgeConfigSpec.IntValue config, int effective) {
        return config.get() < 0 ? effective : config.get();
    }

    private static long currentLong(ForgeConfigSpec.LongValue config, long effective) {
        return config.get() < 0 ? effective : config.get();
    }

    private static double currentDouble(ForgeConfigSpec.DoubleValue config, double effective) {
        return config.get() < 0 ? effective : config.get();
    }

    // ------------------------------------------------------------------
    // 枚举值的界面说法（要能在同一行控件里读完，完整语义在悬停说明里）
    // ------------------------------------------------------------------

    private static String sideName(LayoutSettings.Side side) {
        return tr(side == LayoutSettings.Side.RIGHT
                ? "pickupcard.config.value.align.right" : "pickupcard.config.value.align.barLeft");
    }

    /** 界面上的叫法：火车＝平移，拉幕＝展开可见范围，弹出回弹＝冲过终点再弹回，掉落＝斜着滑进来带下坠小弹。
     *  <p>【穷举 switch】不用 default：枚举再扩档时编译器会逼着这里补映射，
     *  新档就不会因为落到 default 而在界面上显示成别人的名字或英文枚举名。 */
    static String appearName(LayoutSettings.Appear appear) {
        return tr(switch (appear) {
            case SLIDE -> "pickupcard.config.value.appear.train";
            case CLIP -> "pickupcard.config.value.appear.clip";
            case BOUNCE -> "pickupcard.config.value.appear.bounce";
            case DROP -> "pickupcard.config.value.appear.drop";
        });
    }

    /** 与入场对称的那一半：火车退回 / 淡出 / 拉幕收拢 / 下坠 / 缩放消失。穷举 switch，理由同上。 */
    static String exitName(LayoutSettings.Exit exit) {
        return tr(switch (exit) {
            case FADE -> "pickupcard.config.value.exit.fade";
            case TRAIN -> "pickupcard.config.value.exit.trainBack";
            case WIPE -> "pickupcard.config.value.exit.wipe";
            case FALL -> "pickupcard.config.value.exit.fall";
            case SCALE -> "pickupcard.config.value.exit.scale";
        });
    }

    private static String countName(CountFormat format) {
        return switch (format) {
            case X_PREFIX -> "×64";
            case PLAIN -> "64";
            case ABBREVIATED -> "+1.2K";
            default -> "+64";
        };
    }

    private static String mergeName(MergeMode mode) {
        return switch (mode) {
            case SAME_ITEM -> tr("pickupcard.config.value.merge.sameItem");
            case SAME_ITEM_KEEP_NAMED -> tr("pickupcard.config.value.merge.keepNamed");
            case NEVER -> tr("pickupcard.config.value.merge.never");
            default -> tr("pickupcard.config.value.merge.strict");
        };
    }

    /** 屏满策略的两档：顶掉最老的旧卡 / 排队等位（旧行为）。 */
    private static String fullPolicyName(FullPolicy policy) {
        return tr(policy == FullPolicy.QUEUE
                ? "pickupcard.config.value.full.queue" : "pickupcard.config.value.full.replace");
    }

    /** 数字含义的两档：本次拾取数（带进账符号）/ 背包持有总数（实时跟随物品栏）。穷举 switch，理由同 appearName。 */
    static String countModeName(CountMode mode) {
        return tr(switch (mode) {
            case PICKUP -> "pickupcard.config.value.countmode.pickup";
            case TOTAL -> "pickupcard.config.value.countmode.total";
        });
    }
}
