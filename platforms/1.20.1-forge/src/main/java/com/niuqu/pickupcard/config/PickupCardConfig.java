package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.filter.FilterSettings;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.style.Theme;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 玩家可见的设置。
 * <p>
 * 【外观在这里，但只是"改动"】卡面的默认值仍然只有一处真源：主题文件
 * （{@code styles/*.json}，资源包可覆盖）。{@code [style]} 段存的是**玩家改过的项**，
 * 每一项都能取 -1 = 跟随主题 —— 所以不存在"两处真源"：主题给默认值，配置只覆盖改过的。
 * 配置界面写的就是这一段，界面自己不另存一份。
 * <p>
 * 另一部分是"行为偏好"：停留多久、怎么合并、过滤谁。
 * <p>
 * 【配置项的名字与注释是玩家唯一会直接读到的文字】一律说人话，不许用比喻或代号 ——
 * 这条是硬规则（见 docs/architecture.md 的「配置界面」一节）。
 */
public final class PickupCardConfig {

    private static final ForgeConfigSpec SPEC;
    /** 包级可见：配置界面在同一个包里直接读写它 —— 界面不另存一份值，否则迟早出现
     *  "界面显示 5、实际画 9"这种两处都"对"的 bug。 */
    static final Values VALUES;

    static {
        Pair<Values, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Values::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private PickupCardConfig() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    /** 采样一份当前值。热路径上每次读几个内存字段，代价可忽略，换来的是改配置不用重启。 */
    public static PickupCardSettings snapshot() {
        return new PickupCardSettings(
                VALUES.holdMs.get(),
                VALUES.exitMs.get(),
                VALUES.mergeMode.get(),
                VALUES.maxOnScreen.get(),
                VALUES.queueSize.get(),
                VALUES.countFormat.get(),
                VALUES.enabled.get(),
                VALUES.showItemName.get(),
                VALUES.showItemId.get(),
                VALUES.nameMaxWidth.get()).sanitized();
    }

    /**
     * 采样布局设置。下面那一组键的注释一律按"人话"写：配置项是玩家唯一会直接读到的文字，
     * 比喻、内部代号、黑话在那里就是 bug。
     */
    public static LayoutSettings layoutSnapshot() {
        return new LayoutSettings(
                VALUES.appearMode.get(),
                VALUES.exitMode.get(),
                VALUES.align.get(),
                VALUES.separation.get().floatValue(),
                VALUES.scalePercent.get(),
                VALUES.anchorX.get().floatValue(),
                VALUES.anchorY.get().floatValue(),
                VALUES.mirrorCard.get()).sanitized();
    }

    /** 采样过滤三表。列表元素不做校验——坏规则由 FilterRule.parse 静默跳过。 */
    public static FilterSettings filterSnapshot() {
        return new FilterSettings(
                List.copyOf(VALUES.blacklist.get()),
                List.copyOf(VALUES.whitelist.get()),
                List.copyOf(VALUES.muteList.get()));
    }

    /**
     * 三张名单现在的样子，一行字符串。
     * <p>【为什么要有它】界面上"加一条"走的是键盘路径，截图只能证明"框里有字"，
     * 证明不了"回车真的写进了配置"。把这行打进日志，才能把界面动作和配置文件对上账。
     */
    public static String filterDump() {
        FilterSettings f = filterSnapshot();
        return "黑名单" + f.blacklist() + " 白名单" + f.whitelist() + " 静音" + f.muteList();
    }

    /**
     * 两个锚点值一行（给 harness 的日志：拖拽编辑场"写没写进配置"，截图看不出来，
     * 只有数字能对账）。
     */
    public static String anchorDump() {
        return "anchorX=" + VALUES.anchorX.get() + " anchorY=" + VALUES.anchorY.get();
    }

    /** 当前主题。 */
    public static Theme theme() {
        return VALUES.theme.get();
    }

    /**
     * 玩家改过的外观项。<b>-1 一律表示"没改过"，不覆盖主题</b> ——
     * 所以这里不会把主题的值抄一份进来，换主题依旧生效。
     */
    public static StyleOverrides styleOverrides() {
        StyleOverrides.Builder b = StyleOverrides.builder();
        int radius = VALUES.stCornerRadius.get();
        if (radius >= 0) {
            b.cornerRadius(radius);
        }
        int padH = VALUES.stPaddingH.get();
        if (padH >= 0) {
            b.paddingH(padH);
        }
        int padV = VALUES.stPaddingV.get();
        if (padV >= 0) {
            b.paddingV(padV);
        }
        int gap = VALUES.stGap.get();
        if (gap >= 0) {
            b.gap(gap);
        }
        int icon = VALUES.stIconSize.get();
        if (icon >= 0) {
            b.iconSize(icon);
        }
        int bar = VALUES.stBarWidth.get();
        if (bar >= 0) {
            b.barWidth(bar);
        }
        int barInset = VALUES.stBarInsetY.get();
        if (barInset >= 0) {
            b.barInsetY(barInset);
        }
        int borderW = VALUES.stBorderWidth.get();
        if (borderW >= 0) {
            b.borderWidth(borderW);
        }
        // 颜色：空串或写坏了都当作"没改过"，回到主题 —— 一个手滑不该把卡面变成透明。
        // 解析放 shared（StyleOverrides.parseArgb），因为它是纯函数、能离线单测。
        StyleOverrides.parseArgb(VALUES.stFillTop.get()).ifPresent(b::fillTop);
        StyleOverrides.parseArgb(VALUES.stFillBottom.get()).ifPresent(b::fillBottom);
        StyleOverrides.parseArgb(VALUES.stBorder.get()).ifPresent(b::border);
        StyleOverrides.parseArgb(VALUES.stNameColor.get()).ifPresent(b::nameColor);
        long enterMs = VALUES.stEnterMs.get();
        if (enterMs >= 0) {
            b.enterMs(enterMs);
        }
        long bumpMs = VALUES.stBumpMs.get();
        if (bumpMs >= 0) {
            b.bumpMs(bumpMs);
        }
        long reviveMs = VALUES.stReviveMs.get();
        if (reviveMs >= 0) {
            b.reviveMs(reviveMs);
        }
        int enterOn = VALUES.stEnterEnabled.get();
        if (enterOn >= 0) {
            b.enterEnabled(enterOn == 1);
        }
        int bumpOn = VALUES.stBumpEnabled.get();
        if (bumpOn >= 0) {
            b.bumpEnabled(bumpOn == 1);
        }
        return b.build();
    }

    /** 外观项一律"−1 = 没改过"，所以范围从 -1 起。 */
    private static ForgeConfigSpec.IntValue styleInt(ForgeConfigSpec.Builder builder, String name,
                                                    String comment, int max) {
        return builder.comment(comment, "-1 = 没改过（用主题里的值，也就是「跟随主题」）。")
                .defineInRange(name, -1, -1, max);
    }

    /**
     * 颜色键：默认值是<b>空串</b>（= 没改过），不是一个颜色。
     * <p>【为什么不用 -1 那套】颜色是 32 位数，没有"负数"这种哨兵可用；空串既不会和任何
     * 合法颜色撞车，手改 TOML 时也一眼看得出"这项没设过"。
     */
    private static ForgeConfigSpec.ConfigValue<String> styleColor(ForgeConfigSpec.Builder builder,
                                                                  String name, String comment) {
        return builder.comment(comment, "格式 #AARRGGBB（#RRGGBB 当作不透明）；留空 = 没改过，用主题里的颜色。")
                .define(name, "");
    }

    /** 配置项的定义。行为偏好与 {@link PickupCardSettings}/{@link FilterSettings} 对应。 */
    static final class Values {

        final ForgeConfigSpec.BooleanValue enabled;
        final ForgeConfigSpec.BooleanValue showItemName;
        final ForgeConfigSpec.BooleanValue showItemId;
        final ForgeConfigSpec.IntValue nameMaxWidth;
        final ForgeConfigSpec.LongValue holdMs;
        final ForgeConfigSpec.LongValue exitMs;
        final ForgeConfigSpec.EnumValue<com.niuqu.pickupcard.notice.MergeMode> mergeMode;
        final ForgeConfigSpec.IntValue maxOnScreen;
        final ForgeConfigSpec.IntValue queueSize;
        final ForgeConfigSpec.EnumValue<CountFormat> countFormat;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> whitelist;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> muteList;
        final ForgeConfigSpec.EnumValue<LayoutSettings.Appear> appearMode;
        final ForgeConfigSpec.EnumValue<LayoutSettings.Exit> exitMode;
        final ForgeConfigSpec.EnumValue<LayoutSettings.Side> align;
        final ForgeConfigSpec.BooleanValue mirrorCard;
        final ForgeConfigSpec.DoubleValue separation;
        final ForgeConfigSpec.IntValue scalePercent;
        final ForgeConfigSpec.DoubleValue anchorX;
        final ForgeConfigSpec.DoubleValue anchorY;

        // ---- [style] 外观：全部用 -1 表示"跟随主题" ----
        final ForgeConfigSpec.EnumValue<Theme> theme;
        final ForgeConfigSpec.IntValue stCornerRadius;
        final ForgeConfigSpec.IntValue stPaddingH;
        final ForgeConfigSpec.IntValue stPaddingV;
        final ForgeConfigSpec.IntValue stGap;
        final ForgeConfigSpec.IntValue stIconSize;
        final ForgeConfigSpec.IntValue stBarWidth;
        final ForgeConfigSpec.IntValue stBarInsetY;
        final ForgeConfigSpec.IntValue stBorderWidth;
        final ForgeConfigSpec.ConfigValue<String> stFillTop;
        final ForgeConfigSpec.ConfigValue<String> stFillBottom;
        final ForgeConfigSpec.ConfigValue<String> stBorder;
        final ForgeConfigSpec.ConfigValue<String> stNameColor;
        final ForgeConfigSpec.LongValue stEnterMs;
        final ForgeConfigSpec.LongValue stBumpMs;
        final ForgeConfigSpec.LongValue stReviveMs;
        final ForgeConfigSpec.IntValue stEnterEnabled;
        final ForgeConfigSpec.IntValue stBumpEnabled;

        Values(ForgeConfigSpec.Builder builder) {
            builder.comment("Pickup Card —— 拾取卡片提示（纯客户端）").push("notice");

            enabled = builder
                    .comment("总开关。关掉之后捡东西不再弹卡。",
                            "已经显示出来的卡会立刻清掉 —— 关掉再打开不会涌出一堆积压的旧卡。")
                    .define("enabled", true);

            showItemName = builder
                    .comment("显示物品名。关掉只剩「竖条 + 图标 + 数量」，卡片会明显变窄。")
                    .define("showItemName", true);

            showItemId = builder
                    .comment("显示物品 ID（minecraft:stone）而不是它的名字。终端味最重。")
                    .define("showItemId", false);

            nameMaxWidth = builder
                    .comment("物品名最大宽度（像素）。超出就截断加省略号，0 = 按屏宽比例自动。")
                    .defineInRange("nameMaxWidth", 0, 0, 600);

            holdMs = builder
                    .comment("一张卡在屏上停留多久（毫秒），从最近一次被刷新算起。",
                            "连捡同一件东西会不断刷新这个计时，所以连捡时不会闪。")
                    .defineInRange("holdMs", 4_000L, 200L, 60_000L);

            exitMs = builder
                    .comment("退场动画时长（毫秒）：最老的那张被顶出屏幕后淡出多久。",
                            "渲染层按它决定退场动画播多久；0 = 卡片直接消失。",
                            "320 → 480：真机反馈是「淡出这一下太快」。")
                    .defineInRange("exitMs", 480L, 0L, 5_000L);

            builder.pop();

            builder.comment("合并：哪些拾取算同一件东西（一张卡的数字在滚，还是弹好几张）").push("merge");
            mergeMode = builder
                    .comment("SAME_NBT（默认）：同名同 NBT —— 改名、附魔、自定义数据各占一张卡。",
                            "SAME_ITEM：同名就并，忽略 NBT（附魔书、药水会并成一张）。",
                            "SAME_ITEM_KEEP_NAMED：同名就并，但改过名字的不并。",
                            "NEVER：从不合并，每次拾取单开一张（一次捡 20 样东西会看到 20 张）。")
                    .defineEnum("mode", com.niuqu.pickupcard.notice.MergeMode.SAME_NBT);
            builder.pop();

            builder.comment("布局").push("layout");

            maxOnScreen = builder
                    .comment("同时在屏最多几张。满了之后新的拾取先排队，不再顶掉别人。")
                    .defineInRange("maxOnScreen", 5, 1, 16);

            queueSize = builder
                    .comment("排队上限：屏满时最多先排几张（先来先上屏）。",
                            "0 = 不排队 —— 这时屏满之后的拾取会直接丢掉（0.1.0 的语义）。")
                    .defineInRange("queueSize", 9, 0, 128);

            scalePercent = builder
                    .comment("卡片缩放（百分比）。0 = 自动：一摞卡塞不进 HUD 带之上就按比例缩小，",
                            "最多缩到 60%，还不够才少显示几张。手动档 50..200。",
                            "注意：预览面板按 100% 画，自动档的实际倍率要看游戏里的画面。")
                    .defineInRange("scalePercent", LayoutSettings.AUTO_SCALE,
                            LayoutSettings.AUTO_SCALE, LayoutSettings.MAX_SCALE_PERCENT);

            appearMode = builder
                    .comment("卡片出现时怎么展开（内容从竖条右侧出现的那一下）。",
                            "  SLIDE = 火车：内容保持原样，从竖条后面平移出来；数字端先进视野。",
                            "  CLIP  = 拉幕：内容不动，可见范围从左往右展开；图标端先露。")
                    .defineEnum("appearMode", LayoutSettings.Appear.SLIDE);

            exitMode = builder
                    .comment("卡片怎么消失。三种都叠加透明度下降，不会硬切。",
                            "  TRAIN = 火车退回：内容整块平移回竖条后面，与火车入场对称（默认）。",
                            "  FADE  = 淡出：原地变透明。",
                            "  WIPE  = 拉幕收拢：可见范围从右往左收窄，与拉幕入场对称。")
                    .defineEnum("exitMode", LayoutSettings.Exit.TRAIN);

            align = builder
                    .comment("水平对齐：锚线（anchorX）管的是卡的哪一条边。",
                            "  LEFT  = 竖条左缘贴锚线：一摞卡的竖条成一条竖线。",
                            "  RIGHT = 卡片右缘贴锚线：右缘齐、左缘随卡宽参差（默认）。",
                            "    —— 即界面上的「右边缘对齐」预设。2026-09-20 起出厂默认 RIGHT：",
                            "    已保存过本键的配置文件不会自动跟着变，想要右缘对齐请在界面里点一下。")
                    .defineEnum("align", LayoutSettings.Side.RIGHT);

            mirrorCard = builder
                    .comment("镜像卡片：竖条移到卡片最右缘，往左依次是图标、名字、数量，",
                            "  入场/退场动画的方向也跟着镜像（内容仍从竖条后面滑出/缩回）。",
                            "  false = 常规：竖条在最左（默认）。")
                    .define("mirrorCard", false);

            anchorX = builder
                    .comment("卡堆锚点的横坐标（0~1 = 屏幕宽度的比例）：第一张卡的竖条左缘停在这儿。",
                            "-1（默认）= 自动：让最宽的那张卡右缘正好落在右边距上（跟着画布算），",
                            "  一摞卡的竖条因此成一条竖线。",
                            "【推荐改法】配置界面「位置与堆叠」页 → 「拖拽调整位置」，不用手写这个数。",
                            "旧键 stickTo / leftEdge 已废弃：读进来会被忽略。")
                    .defineInRange("anchorX", -1.0, -1.0, 1.0);

            anchorY = builder
                    .comment("卡堆锚点的纵坐标（0~1 = 屏幕高度的比例）：第一张卡（最新）的顶边停在这儿，",
                            "新卡永远从锚线出现、旧的被顶上去（卡堆向上生长）。",
                            "-1（默认）= 自动：贴着 HUD 带上方（常见画布上同屏上限真能放满）。",
                            "自定义值拖到哪儿就是哪儿；锚线上放不下几张时，放不下的先排队。",
                            "【推荐改法】配置界面「位置与堆叠」页 → 「位置 → 拖拽调整」。")
                    .defineInRange("anchorY", -1.0, -1.0, 1.0);

            separation = builder
                    .comment("两张卡之间的空隙（像素）。它跟卡内间隙（[style] gap）不是一回事：",
                            "一个是「卡与卡」，一个是「框与框」。")
                    .defineInRange("separation", (double) LayoutSettings.DEFAULT_SEPARATION, 0.0, 32.0);

            builder.pop();

            builder.comment("数字").push("count");
            countFormat = builder
                    .comment("数量的写法。PLUS = +64（默认），X_PREFIX = ×64，",
                            "PLAIN = 64，ABBREVIATED = +1.2K。")
                    .defineEnum("format", CountFormat.PLUS);
            builder.pop();

            builder.comment("外观：这里只放玩家改过的项。-1 / 空串 = 没改过，用主题里的值。",
                            "【配置界面看到的是什么】界面显示的是**生效值**（主题 + 你的改动），",
                            "不显示「没改过」这个状态；你拨了哪一项，那一项就写进这里变成固定值。",
                            "想让它重新跟着主题走：那一项改回 -1（颜色改成空串），或者删掉整行。")
                    .push("style");
            theme = builder
                    .comment("主题：DARK = 深色（默认），LIGHT = 浅色。",
                            "主题给的是整套默认值，下面那些改动只覆盖你改过的项 —— 换主题不会把你的改动丢掉。")
                    .defineEnum("theme", Theme.DARK);
            stCornerRadius = styleInt(builder, "cornerRadius", "三个框的圆角半径（像素）", 16);
            stPaddingH = styleInt(builder, "paddingH", "框内水平内边距（像素）", 16);
            stPaddingV = styleInt(builder, "paddingV", "框内垂直内边距（像素）。卡高 = 图标边长 + 上下各一份它", 16);
            stGap = styleInt(builder, "gap", "框与框之间、名字与数量之间的间隙（像素）", 16);
            stIconSize = styleInt(builder, "iconSize", "物品图标边长（像素）。原版贴图是 16，取 16 = 不缩放最清晰", 64);
            stBarWidth = styleInt(builder, "barWidth", "稀有度竖条宽度（像素）", 24);
            stBarInsetY = styleInt(builder, "barInsetY", "竖条上下各内缩多少（像素）。0 = 与卡片齐平", 16);
            stBorderWidth = styleInt(builder, "borderWidth", "框描边粗细（像素）。0 = 不描边", 4);
            stFillTop = styleColor(builder, "fillTop", "卡面底色（上端）");
            stFillBottom = styleColor(builder, "fillBottom", "卡面底色（下端）。两个色写成一样就是纯色");
            stBorder = styleColor(builder, "border", "框描边颜色");
            stNameColor = styleColor(builder, "nameColor", "物品名颜色");
            stEnterMs = builder
                    .comment("入场动画总时长（毫秒）。竖条占前 50%，内容 20% 起跑、84% 到位，尾巴静止。",
                            "-1 = 没改过（用主题里的值）。")
                    .defineInRange("enterMs", -1L, -1L, 5_000L);
            stBumpMs = builder
                    .comment("合并时数字跳动时长（毫秒）。", "-1 = 跟随主题（默认）。")
                    .defineInRange("bumpMs", -1L, -1L, 5_000L);
            stReviveMs = builder
                    .comment("淡回时长（毫秒）：退场途中被再次拾起时，不透明度补回全不透明的耗时。",
                            "160 太短：回来比离开（exitMs）快 3 倍，屏幕上是\"文字和图标突然闪一下\"。",
                            "-1 = 跟随主题（默认 300）。")
                    .defineInRange("reviveMs", -1L, -1L, 5_000L);
            stEnterEnabled = styleInt(builder, "enterEnabled", "入场动画开关。0 = 关（动画敏感玩家可以关掉，卡片直接出现）", 1);
            stBumpEnabled = styleInt(builder, "bumpEnabled", "合并时数字跳动开关。0 = 关", 1);
            builder.pop();

            builder.comment("过滤。规则写法：minecraft:stone = 物品，#forge:ores = tag，@somebotania = 整个 mod")
                    .push("filter");
            blacklist = builder
                    .comment("黑名单：命中则不弹卡。默认是空的 —— 也就是说默认每一次拾取都会弹卡，",
                            "包括泥土、圆石、沙子。嫌刷屏就往这里加（例如 minecraft:cobblestone）。")
                    .defineList("blacklist", List.of(), o -> o instanceof String);

            whitelist = builder
                    .comment("白名单：命中则永远弹卡并强调。优先级最高，压过黑名单。")
                    .defineList("whitelist", List.of(), o -> o instanceof String);

            muteList = builder
                    .comment("静音名单：命中照常弹卡，但没有稀有提示音，原版拾取音也压掉。")
                    .defineList("muteList", List.of(), o -> o instanceof String);
            builder.pop();
        }
    }
}
