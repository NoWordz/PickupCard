package com.niuqu.pickupcard.dev;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.config.AnchorEditScreen;
import com.niuqu.pickupcard.config.CardGridScreen;
import com.niuqu.pickupcard.config.PickupCardConfig;
import com.niuqu.pickupcard.config.PickupCardConfigScreen;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 调试屏的入口：一个按键（F9）、一条客户端命令（{@code /pickupcarddev}），
 * 以及一条无人值守的自动驱动（{@code -Dpickupcard.harness.auto=shot}）。
 * <p>
 * 【为什么只在开发环境注册】{@link #register} 只被开发环境分支调用（见 mod 入口）。
 * 这里的内部类<b>刻意不用</b> {@code @Mod.EventBusSubscriber} —— 那个注解会被
 * 自动扫描注册，做不到"只在 dev 生效"；手工 register 才真的把玩家端挡在外面。
 * <p>
 * 【为什么要有自动驱动】"编译过、单测绿"对渲染层是零证据——这个项目已经用三个真机
 * bug 换过这条教训。而"要有人坐在机器前按键才能验"同样是个瓶颈：它让验证没法进 CI，
 * 也没法在改动前后各跑一遍做对比。自动驱动把验证变成一条命令。
 */
public final class DevHarness {

    public static final KeyMapping OPEN = new KeyMapping(
            "key.pickupcard.harness", GLFW.GLFW_KEY_F9, "key.categories.misc");

    private DevHarness() {
    }

    /** 只在 {@code !FMLEnvironment.production} 时调用。 */
    public static void register(IEventBus modBus) {
        modBus.register(ModEvents.class);
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);
    }

    static final class ModEvents {

        @SubscribeEvent
        static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    static final class ForgeEvents {

        @SubscribeEvent
        static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            AutoDrive.tick(mc);
            while (OPEN.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new DevCardScreen());
                }
            }
        }

        @SubscribeEvent
        static void onClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("pickupcarddev").executes(ctx -> {
                Minecraft.getInstance().setScreen(new DevCardScreen());
                return 1;
            }));
        }
    }

    /**
     * 无人值守驱动：等客户端加载完 → 推全部样例 → 拍图 → 退出。
     * <p>
     * 由系统属性 {@code pickupcard.harness.auto} 开启，构建侧用
     * {@code ./gradlew runClient -PharnessAuto=shot} 转发。默认 {@code off}，
     * 所以平时它一行都不跑。
     */
    static final class AutoDrive {

        private static final String MODE = System.getProperty("pickupcard.harness.auto", "off");
        /** 等客户端把加载屏走完再动手；早于这个时刻开屏会拍到半张加载界面。 */
        private static final int WARMUP_TICKS = 80;
        /**
         * 展开到一半时先拍一张 —— 只看"已经就位"的稳态，等于没验动画。
         * <p>
         * 【为什么是 5 tick】入场 2026-09-17 从 800ms 压到 480ms（内容 96→403ms），
         * 原来那 8 tick（400ms）拍到的**已经是终态**了 —— 一张"验动画"的图拍到终态，
         * 就等于没验。5 tick ≈ 250ms 落在内容窗口的中段。
         */
        private static final int MID_SHOT = 5;
        /** 刚起步的一帧（2 tick ≈ 100ms）：内容刚起跑，专门验"隧道口"有没有真裁在竖条右侧。 */
        private static final int EARLY_SHOT = 2;
        /** 开屏后等入场动画播完再拍。 */
        private static final int SHOT_AFTER_OPEN = 40;
        /**
         * 入场逐帧 trace 记到第几 tick：入场 480ms ≈ 10 tick，记到 22 顺带覆盖扫光与
         * "截图回读之后那一帧"（{@code Screenshot.grab} 会把 GPU 拉停一下，紧跟着的那一帧
         * 因此可能异常贵 —— 那是 harness 自己的产物，不是玩家的路径，必须能分辨出来）。
         */
        private static final int ENTER_TRACE_TICKS = 22;
        /**
         * 拍完基础那张之后还跑多少 tick 再退出。
         * <p>
         * 【为什么是 70 而不是 40】卡的自然退场由 holdMs 决定（默认 4000ms = 80 tick），
         * 而 ③a 之后「屏满」只排队、不再淘汰旧卡 —— 从前是「推第 6 张」制造退场，现在退场
         * 只剩自然到点这一条路。窗口必须把这 80 tick 那一下包进去，否则「看见退场」永远不成立
         * （第一版就是这么空跑的：日志里 `退场/淡回` 一行都没有）。
         */
        private static final int QUIT_AFTER_SHOT =
                Integer.getInteger("pickupcard.harness.quitAfter", 70);
        /**
         * 退场淡出：稳态那张拍完之后再推一张把最老的挤掉，隔 3 tick（约 150ms）拍中段。
         * <p>
         * 【为什么非要多拍这一张】只看稳态的截图，等于没验过任何动画 —— 退场这条以前只有
         * 12px 位移、没有淡出，而"到底淡没淡"在静止的图上根本看不出来。拍早了那张还没开始
         * 淡，拍晚了它已经下线，所以这两个数是朝着 320ms 的中段取的。
         */
        private static final int EXIT_PUSH_AFTER = SHOT_AFTER_OPEN + 2;
        private static final int EXIT_SHOT_AFTER = EXIT_PUSH_AFTER + 3;
        /**
         * 退场连拍：从"看见退场"起第 {@value #EXIT_LATE_TICK} tick 开始，每
         * {@value #EXIT_LATE_EVERY} tick 一张，共 {@value #EXIT_LATE_FRAMES} 张。
         * <p>【必须先读这条，否则这组截图一定是废的】{@code tickHud} 挂在
         * {@code TickEvent.ClientTickEvent}（END 相位）上，也就是 <b>20Hz</b>；而退场按毫秒走。
         * 默认 {@code exitMs=480} 只有约 <b>9.6 tick</b> —— 20Hz 的网格分辨不了它，
         * 连拍只会拍到"卡已经没了"的空屏（2026-09-18 就这么白做过一次 A/B：抓到的几张
         * 逐像素相同，其实是空屏相同）。所以这组连拍<b>只在把淡出拉长之后才有意义</b>：
         * <pre>run/config/pickupcard-client.toml: exitMs = 4800  （96 tick）</pre>
         * 上面的数字按 4800ms 取的，覆盖 alpha 0.95 → 0.005；同一份配置下
         * {@code queueSize = 0} 保证没有卡补位，卡堆不动，截图上取的区域才不用跟着走。
         * <p>【为什么是连拍而不是"就拍淡到一半那一张"】
         * {@code tick} 与毫秒的换算随帧率变，想精确命中某一档不透明度只能靠猜，猜错过。
         * 连拍一串之后在图上按"强调条亮度"对齐（它是 alpha 的线性代理），
         * 就能把三条通道（外壳 / 图标 / 文字）放在同一张图上比 —— 谁没跟着淡，一眼可见。
         */
        private static final int EXIT_LATE_TICK = 2;
        private static final int EXIT_LATE_EVERY = 6;
        private static final int EXIT_LATE_FRAMES = 14;
        /**
         * 看见第一帧退场之后，再过几 tick 就"淡到一半"，那时再捡同一个物品触发淡回。
         * <p>【为什么要能改】淡回一触发，退场就被撤销，连拍窗口<b>剩下的部分全部作废</b>。
         * 上面那组数字要 80 tick 才拍完，而默认的 15 会在中途把它掐掉 —— 所以它得让开。
         * 命令行给 {@code -PharnessReviveAfter=N} 即可（默认 15 是给正常时长用的）。
         */
        private static final int REVIVE_AFTER_EXIT_SEEN =
                Integer.getInteger("pickupcard.harness.reviveAfter", 15);
        /** 淡回开始之后再过几帧拍一张。 */
        private static final int REVIVE_SHOT_AFTER = 2;

        /**
         * 「同一样东西又捡到了」的探针：在这一 tick 把<b>最新那张卡</b>的物品再捡一次。
         * <p>【为什么这个探针必须有】{@code bumpMs} / {@code bumpEnabled} 从第一版起就是
         * 主题与配置里的正式项，而直到 2026-09-18 才发现 {@code CardCanvas#bumpOf} <b>一次都
         * 没有被调用过</b> —— 整个"数字跳动"是死参数，配置里那一格调了没有任何反应。
         * 没有探针的东西就会这样：编译过、单测绿、真机上什么都不发生。
         * <p>取 {@code SHOT_AFTER_OPEN + 3}：那时样例卡都还在、且都还没开始退场，
         * 于是这次再捡是纯粹的"合并"，不会跟救回混在一起。
         */
        private static final int MERGE_BUMP_AFTER = SHOT_AFTER_OPEN + 3;
        /** 合并连拍：从再捡那一刻起每 {@value #MERGE_EVERY} tick 一张，共 {@value #MERGE_FRAMES} 张。 */
        private static final int MERGE_EVERY = 3;
        private static final int MERGE_FRAMES = 9;

        /** 探针状态：退场、淡回、合并各只做一次。 */
        private static boolean mergeBumpDone;

        private static boolean exitSeen;
        private static boolean exitShotDone;
        private static boolean reviveDone;
        private static int ticksSinceExitSeen;
        private static int ticksSinceRevive;

        private static final List<List<CardFixtures.Fixture>> PAGES = CardFixtures.pages();
        /** 卡样例页之后的矢量 spike 页（不画卡，只画图元与外壳探针）。 */
        private static final int SPIKE_PAGE = PAGES.size();
        /**
         * 最后一页：测量页。纯黑底、无辅助线、无读数 —— 给像素对照一张干净的输入。
         * <p>
         * 【为什么它是一页而不是"截图时顺手关掉"】对照的输入必须是可重复的：
         * 同一组卡、同一个底、同一个状态。做成页面，它就有名字、有日志、有产物。
         */
        private static final int MEASURE_PAGE = PAGES.size() + 1;
        private static final int TOTAL_PAGES = PAGES.size() + 2;

        private static DevCardScreen screen;

        private static int ticks;
        private static int page = -1;
        private static int sinceInject = -1;

        private AutoDrive() {
        }

        static boolean enabled() {
            return !"off".equalsIgnoreCase(MODE);
        }

        /**
         * {@code -PharnessGuiScale=N}：运行时把 GUI 缩放定死（1..5）。
         * <p>
         * 【为什么要这个开关】有些分支只在"画布很小"时才成立：自动缩放档、三列退让、列表滚动。
         * dev 窗口默认 1280×720 在 guiScale 3 下是 427×240，这三条一辈子走不到 —— 于是它们
         * 只有单测级证据。{@code options.txt} 里写 guiScale 试过，不生效（原因没查到）；
         * 这里直接用 1.20.1 的 public 入口：{@code options.guiScale().set(n)} + {@code resizeDisplay()}。
         */
        private static final String GUI_SCALE = System.getProperty("pickupcard.harness.guiScale", "off");

        /** {@code -PharnessAuto=hud}：不打开调试屏，走玩家真正走的那条路。 */
        private static final boolean HUD_ONLY = "hud".equalsIgnoreCase(MODE);
        private static int hudTicks;
        private static boolean hudInjected;
        /** 缩放只应用一次（每 tick 改会让窗口反复重建）。 */
        private static boolean scaleApplied;

        /** {@code -PharnessAuto=config}：打开配置界面 → 截图 → 退出。界面能不能画出来要能被验。 */
        private static final boolean CONFIG_ONLY = "config".equalsIgnoreCase(MODE);
        private static int configTicks;

        /**
         * {@code -PharnessAuto=grid}：第三个形态（网格，A-19）→ 截图 + 读数 → 退出。
         * <p>独立一条时间线：{@code tickConfig} 那串偏移是配置流程的，网格不复用也不会互相干扰。
         */
        private static final boolean GRID_ONLY = "grid".equalsIgnoreCase(MODE);
        private static int gridTicks;

        /** 把请求的 GUI 缩放应用上去（只做一次），并把画布尺寸打进日志。 */
        private static void applyGuiScale(Minecraft mc) {
            if (scaleApplied || GUI_SCALE.equalsIgnoreCase("off")) {
                return;
            }
            // 【为什么必须等进世界、且必须是自家的界面】在加载界面上改缩放 + resize 那个界面，
            // 会把加载流程停在半路（实测：卡在加载屏 26 秒、世界根本没加载出来）。
            // 自家界面允许：配置模式的窗口很短（世界里 + 无界面只有 1 tick），那时才轮得到它。
            // 网格那一屏也要放行 —— 不放行的话缩放函数直接 return、scaleApplied 永远 false，
            // 于是 -PharnessGuiScale 四档全跑在同一个画布上（列数退让的证据根本产不出来，
            // 而日志里连一行警告都没有）。这一条是 A-19 排方案时当场读源码逮到的。
            boolean ourScreen = mc.screen == null
                    || mc.screen instanceof PickupCardConfigScreen
                    || mc.screen instanceof CardGridScreen;
            if (mc.level == null || mc.getOverlay() != null || !ourScreen) {
                return;
            }
            scaleApplied = true;
            try {
                int want = Integer.parseInt(GUI_SCALE.trim());
                // 【为什么直接写 Window，而不是 options.guiScale()】原版那个选项是
                // ClampingLazyMaxIntRange：上限 = calculateScale(0)，也就是**自动档本身** ——
                // 玩家侧根本调不出比自动档更小的画布（1280×720 的极限就是 427×240）。
                // 所以 options.txt 里写 guiScale 也没用（那是这条悬案的答案）。
                // dev 要验"画布很小"的分支，只能直接写窗口的缩放值。
                mc.getWindow().setGuiScale(want);
                if (mc.screen != null) {
                    mc.screen.resize(mc, mc.getWindow().getGuiScaledWidth(),
                            mc.getWindow().getGuiScaledHeight());
                }
                PickupCard.LOGGER.info("[harness-auto] GUI 缩放定死为 {}：画布 {}x{}",
                        mc.getWindow().getGuiScale(), mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight());
            } catch (NumberFormatException e) {
                PickupCard.LOGGER.warn("[harness-auto] harnessGuiScale 给了个不是整数的值：{}", GUI_SCALE);
            }
        }

        static void tick(Minecraft mc) {
            if (!enabled()) return;
            if (HUD_ONLY) {
                tickHud(mc);
                return;
            }
            if (CONFIG_ONLY) {
                tickConfig(mc);
                return;
            }
            if (GRID_ONLY) {
                tickGrid(mc);
                return;
            }
            ticks++;

            if (page < 0) {
                if (ticks < WARMUP_TICKS) return;
                if (mc.getOverlay() != null) return;
                if (mc.screen instanceof DevCardScreen) return;

                screen = new DevCardScreen();
                mc.setScreen(screen);
                PickupCard.LOGGER.info("[harness-auto] 已打开调试屏，共 {} 页，guiScale={}",
                        TOTAL_PAGES, mc.getWindow().getGuiScale());
                advance(mc);
                return;
            }

            sinceInject++;
            if (sinceInject == EARLY_SHOT) {
                // 【为什么还要更早的一帧】入场刚起步那几 tick 才是"隧道口"唯一说了算的时刻：
                // 内容还大幅偏左，有没有裁剪、裁剪线在不在竖条右侧，只有这时候看得出来。
                // 中段那帧（MID_SHOT）是按"入场播到一半"挑的，而那正是曲线把内容推得
                // 差不多到位的时候 —— 拍出来两张几乎一样，等于没验。
                if (page != MEASURE_PAGE) capture(mc, "early");
            } else if (sinceInject == MID_SHOT) {
                // 测量页不拍中途：展开到一半的卡几何是变的，量出来的数没有意义
                if (page != MEASURE_PAGE) capture(mc, "mid");
            } else if (sinceInject == SHOT_AFTER_OPEN) {
                capture(mc, null);
            } else if (sinceInject >= SHOT_AFTER_OPEN + QUIT_AFTER_SHOT) {
                advance(mc);
            }
        }

        static void tickConfig(Minecraft mc) {
            // 【为什么整条流程都门控在"世界进来了"之后】--quickPlaySingleplayer 要几秒才把世界加载
            // 出来，而 tick 从第一帧就开始数；不门控的话"世界里 + 没界面"那个安全窗口会被自己错过
            // （实测：缩放函数一次都没跑，还差点在加载屏上动缩放把加载卡死）。
            if (mc.level == null || mc.getOverlay() != null) {
                return;
            }
            // 【为什么反复开、而不是开一次】quickPlay 期间 MC 自己还会换几次界面（收块屏、地形
            // 加载屏），开一次会被它盖掉 —— 实测截图拍的是世界、日志写着"(不是配置界面)"。
            // 所以：一直开到它真的挂上；**挂上之后再动缩放**（那时没别人会再改窗口）。
            // 【编辑场要放行】拖拽编辑场是配置流程的一部分（点『位置』打开）；不放行的话
            // 它挂上来的下一个 tick 就被这里硬换回配置界面 —— 2026-09-18 第三轮实测抓到，
            // removed() 堆栈点名就是这一行。
            if (!(mc.screen instanceof PickupCardConfigScreen)
                    && !(mc.screen instanceof AnchorEditScreen)) {
                mc.setScreen(new PickupCardConfigScreen(null));
                return;
            }
            applyGuiScale(mc);
            configTicks++;
            if (configTicks == WARMUP_TICKS) {
                mc.setScreen(new PickupCardConfigScreen(null));
                PickupCard.LOGGER.info("[harness-auto] 配置界面已打开");
                return;
            }
            if (configTicks == WARMUP_TICKS + 20) {
                capture(mc, null);
                return;
            }
            // 【为什么不直接改配置值】那样只能证明"配置→渲染"通，证明不了"按钮→配置"通。
            // 这里发的是**真实鼠标事件**（mouseClicked → 按钮 → 写配置 → 渲染重读），
            // 把整条链一起验掉 —— 「配置界面没点过」那条待办就是它。
            if (configTicks == WARMUP_TICKS + 26) {
                PickupCard.LOGGER.info("[harness-auto] 第 1 页控件: {}", configLabels(mc));
                clickByLabel(mc, I18n.get("pickupcard.config.page.layout.name"));            // 切到「位置与堆叠」页
                return;
            }
            if (configTicks == WARMUP_TICKS + 30) {
                // 【为什么晚一帧再 dump】切分类是"下一帧重建"，同一 tick 里 dump 出来的还是旧页 ——
                // 日志写着"点『位置与堆叠』页控件"却列着通用页的项，那种日志比没有还坏。
                PickupCard.LOGGER.info("[harness-auto] 点『位置与堆叠』后: {}", configLabels(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 40) {
                capture(mc, "p2");
                return;
            }
            if (configTicks == WARMUP_TICKS + 44) {
                // 列几何进日志：截图看得出"好不好看"，看不出"在哪一档退让、收没收预览"
                if (mc.screen instanceof PickupCardConfigScreen screen) {
                    PickupCard.LOGGER.info("[harness-auto] 配置列: {}", screen.columnDump());
                    float before = screen.scrollOffsetForHarness();
                    screen.scrollForHarness(-1);
                    float after = screen.scrollOffsetForHarness();
                    PickupCard.LOGGER.info("[harness-auto] 配置列滚一格: 偏移 {} → {}（动没动都要看得见）",
                            Math.round(before), Math.round(after));
                    PickupCard.LOGGER.info("[harness-auto] 滚动后列几何: {}", screen.columnDump());
                }
                return;
            }
            if (configTicks == WARMUP_TICKS + 45) {
                // 【滚动之后必须补一张】滚动偏移要进 Trellis 的布局（见 TrellisColumn.layoutColumn）：
                // 不进去的话标签、悬停底、命中会与宿主那一份整整错开一个偏移量，
                // 而"整齐地错开 13px"这种错在静止的截图里非常像对的 —— 只有这一帧能钉住它。
                capture(mc, "p2-scrolled");
                return;
            }
            if (configTicks == WARMUP_TICKS + 46) {
                // 「位置」那颗钮：点开整屏拖拽编辑场，再 Esc 取消 —— 开关这条链要能自动走通
                clickByLabel(mc, I18n.get("pickupcard.config.row.position.name"));
                return;
            }
            if (configTicks == WARMUP_TICKS + 47) {
                if (!(mc.screen instanceof AnchorEditScreen editor)) {
                    PickupCard.LOGGER.error("[harness-auto] 点『位置』后没进编辑场（当前 {}）",
                            mc.screen == null ? "无界面" : mc.screen.getClass().getSimpleName());
                    return;
                }
                PickupCard.LOGGER.info("[harness-auto] 进编辑场: {}", editor.stateDump());
                // 【A-17：树那一侧的几何】这一行是新读数（老读数一个字没动，第 38 轮那份还能逐字比）：
                // 标题 / 提示 / 两颗按钮的盒子全部从树读 —— 迁树之前它们是 init() 里手算的两个 Rect。
                PickupCard.LOGGER.info("[harness-auto] 进编辑场（树）: {}", editor.treeDump());
                // 真实事件路径拖到 (70%,60%)，dump 出来"变没变"一眼可读
                editor.dragForHarness(0.70, 0.60);
                PickupCard.LOGGER.info("[harness-auto] 拖到 (70%,60%) 后: {}", editor.stateDump());
                PickupCard.LOGGER.info("[harness-auto] 拖到 (70%,60%) 后（树）: {}", editor.treeDump());
                // 2026-09-19 方案一（所见即所得）：拍下括号/锚线/贴边提示 —— 堆被夹住时
                // 锚线还在动，这张图就是"诚实"二字的证据
                capture(mc, "editor-dragged");
                return;
            }
            if (configTicks == WARMUP_TICKS + 48) {
                // 先拍再取消：这一帧才是拖完之后的（上一帧缓冲已按锚点 0.70/0.60 渲染过）
                // —— 括号被夹在边距上 + 「卡已贴边距」提示，方案一所见即所得的定妆照
                capture(mc, "editor-clamped");
                // 【A-17 评审那条真 bug 的回归】编辑场的按钮曾经点不动（蒙层在捕获阶段吃掉了
                // POINTER_DOWN），而当时的 harness 只驱动过拖拽与取消 —— 谁都没点过按钮，
                // 所以三轮真机全绿。这一下点的是真按钮、走真事件路径；拍完照再点，
                // 免得改掉上面那张定妆照的内容。
                if (mc.screen instanceof AnchorEditScreen editor) {
                    PickupCard.LOGGER.info("[harness-auto] 点『回到默认』之后: {}",
                            editor.clickResetForHarness());
                }
                // Esc 取消：配置必须原样（没写盘），回到配置界面
                if (mc.screen instanceof AnchorEditScreen editor) {
                    editor.cancelForHarness();
                }
                PickupCard.LOGGER.info("[harness-auto] Esc 后：{}", PickupCardConfig.anchorDump());
                clickByLabel(mc, I18n.get("pickupcard.config.page.anim.name"));        // 下一项要拖的滑条在动画页
                return;
            }
            if (configTicks == WARMUP_TICKS + 52) {
                long before = CardStage.INSTANCE.previewStyle().enterMs();
                // 【为什么拖到 50% 不是 35%】0.35 × 2000 = 700，吸附到 40 的倍数是 680 ——
                // 撞上 dev 配置目录里上一次拖拽残留的 680，"变了才算通"就永远等不到变化
                // （第三轮实测：680 → 680，其实链路是好的，日志冤枉了它）。
                dragOption(mc, I18n.get("pickupcard.config.row.enterMs.name"), 0.5);
                long after = CardStage.INSTANCE.previewStyle().enterMs();
                PickupCard.LOGGER.info("[harness-auto] 拖『入场时长』到 50%：{} → {}（变了才算拖拽这条链通）",
                        before, after);
                return;
            }
            // ---- 第 ④ 步：预览样例 + 短动画 ----
            // 【为什么点完立刻 dump 一次、过几帧再 dump 一次】动画的"跑了"与"跑到哪"是两件事：
            // 第一行读数是起点（≈0），第二行是终点（=1）。只看终点的话，硬切（没有动画）
            // 和"动画播完了"在日志里长得一模一样。
            if (configTicks == WARMUP_TICKS + 54) {
                PickupCard.LOGGER.info("[harness-auto] 换样例前: {}", configState(mc));
                if (!clickByLabel(mc, I18n.get("pickupcard.config.sample.rare.label"))) {
                    PickupCard.LOGGER.warn("[harness-auto] 这一档画布上没有切样例行（预览收起了）");
                }
                PickupCard.LOGGER.info("[harness-auto] 点『稀有』后: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 55) {
                // 淡入 200ms = 4 tick：第 1 tick 大约是半透明，拍下来才看得见"在淡"
                capture(mc, "preview-fade");
                PickupCard.LOGGER.info("[harness-auto] 换样例淡入中: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 62) {
                capture(mc, "preview");
                PickupCard.LOGGER.info("[harness-auto] 换样例停稳: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 64) {
                clickByLabel(mc, I18n.get("pickupcard.config.sample.longName.label"));     // 长名字那一档：验截断，也验"预览不越界"
                return;
            }
            if (configTicks == WARMUP_TICKS + 66) {
                // 悬停「停留时长」（动画页上的那一项）：太短的行悬停看不出来，这一页正好有
                hoverByLabel(mc, I18n.get("pickupcard.config.row.holdMs.name"));
                PickupCard.LOGGER.info("[harness-auto] 悬停「停留时长」后: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 67) {
                capture(mc, "hover");
                PickupCard.LOGGER.info("[harness-auto] 悬停动画中: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 72) {
                PickupCard.LOGGER.info("[harness-auto] 悬停停稳: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 74) {
                hoverByLabel(mc, null);       // 松开悬停，接着切到布局页看那一摞卡
                clickByLabel(mc, I18n.get("pickupcard.config.page.layout.name"));
                PickupCard.LOGGER.info("[harness-auto] 切页那一帧（换页无动画，强调条该在起点）: {}",
                        configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 76) {
                capture(mc, "page-mid");      // 换页 180ms：第 2 tick 正是中段
                PickupCard.LOGGER.info("[harness-auto] 换页动画中: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 84) {
                capture(mc, "stack");
                PickupCard.LOGGER.info("[harness-auto] 布局页（长名样例在摞里）: {}", configState(mc));
                PickupCard.LOGGER.info("[harness-auto] 配置列: {}", columnDump(mc));
                checkPainted(mc);
                return;
            }
            if (configTicks == WARMUP_TICKS + 86) {
                clickByLabel(mc, I18n.get("pickupcard.config.page.filter.name"));
                PickupCard.LOGGER.info("[harness-auto] 切到过滤页: {}", configState(mc));
                PickupCard.LOGGER.info("[harness-auto] {}", configLabels(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 90) {
                // 一条合法规则：走"点 → 逐字 → 回车"的完整键盘路径
                typeByLabel(mc, I18n.get("pickupcard.config.filter.addRow"), "minecraft:cobblestone");
                PickupCard.LOGGER.info("[harness-auto] 加了一条合法规则后: {}", filterDump(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 94) {
                // 一条缺命名空间的：必须被拒，而且要在界面上说出来（不是静默吞掉）
                typeByLabel(mc, I18n.get("pickupcard.config.filter.addRow"), "cobblestone");
                PickupCard.LOGGER.info("[harness-auto] 加了一条非法规则后: {}", filterDump(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 96) {
                // 停在刚加进去的那一条上：底部那行要能看全规则原文（标签格太窄，屏上是截断的）
                hoverByLabel(mc, "minecraft:cobblestone");
                PickupCard.LOGGER.info("[harness-auto] 悬停规则行: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 98) {
                capture(mc, "filter");
                PickupCard.LOGGER.info("[harness-auto] 过滤页: {}", configState(mc));
                PickupCard.LOGGER.info("[harness-auto] 过滤页控件: {}", configLabels(mc));
                PickupCard.LOGGER.info("[harness-auto] 配置列: {}", columnDump(mc));
                checkPainted(mc);
                return;
            }
            // ---- A-11：键盘路由（焦点只有一份真相，在树里）----
            // 【为什么这两行必须成对】"焦点哪一行"与"焦点有没有交还"是这件事的两半：
            // 只有第一行的话，"焦点永远赖在文本框上"和"焦点确实在文本框上"在日志里长得一样。
            if (configTicks == WARMUP_TICKS + 100) {
                // 点进「加一条」那个输入框（过滤页三个名单各有一个，按标签取到的第一个是黑名单的），
                // 敲几个字符但不回车 —— 键走的是"树 → 焦点控件"这条路，草稿停在控件里
                if (clickByLabel(mc, I18n.get("pickupcard.config.filter.addRow"))) {
                    typeChars(mc, "abc");
                }
                PickupCard.LOGGER.info("[harness-auto] 键盘路由: {}", keyboardDump(mc));
                return;
            }
            // ---- A-15b：Tab / Shift+Tab 焦点遍历（焦点看得见了，现在还要能用键走）----
            // 【为什么这两条要紧挨着上面那一条】上一条把焦点停在「Add one」那一行（第 3 行）。
            // 从"已知在第 3 行"出发按 Tab，落点是不是第 4 行，才是这条路径唯一的证据 ——
            // 单看一个行号说明不了"它往前走了"。
            if (configTicks == WARMUP_TICKS + 101) {
                pressTab(mc, true);
                PickupCard.LOGGER.info("[harness-auto] Tab 之后: {}", keyboardDump(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 102) {
                pressTab(mc, false);
                PickupCard.LOGGER.info("[harness-auto] Shift+Tab 之后: {}", keyboardDump(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 103) {
                // 再点另一行（黑名单表头那颗只读钮）：焦点必须换到那一行去 ——
                // 换焦点走的是"先给旧的 BLUR、再给新的 FOCUS"，旧控件由此收尾（停编辑、丢草稿）
                clickByLabel(mc, I18n.get("pickupcard.config.filter.blacklist"));
                PickupCard.LOGGER.info("[harness-auto] 键盘路由: {}", keyboardDump(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 104) {
                // 点刚加进去那一条的「删除」：列表必须真的短回去
                clickByLabel(mc, "minecraft:cobblestone");
                PickupCard.LOGGER.info("[harness-auto] 删掉那条之后: {}", filterDump(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 106) {
                capture(mc, "filter-after-delete");
                PickupCard.LOGGER.info("[harness-auto] 删完的过滤页: {}", configLabels(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 107) {
                // 2026-09-19 重构回归线：展开方式搬进动画页 + 恢复默认单一出处 + 舞台唯一身份
                clickByLabel(mc, I18n.get("pickupcard.config.page.anim.name"));
                return;
            }
            if (configTicks == WARMUP_TICKS + 108) {
                // 恢复默认：恢复后「消失方式」必须是正本默认（火车退回）——
                // 从前手抄默认值漂成 FADE，点恢复反而把设置改错
                PickupCard.LOGGER.info("[harness-auto] 恢复前: {}", configLabels(mc));
                clickByLabel(mc, I18n.get("pickupcard.config.button.restore"));
                return;
            }
            if (configTicks == WARMUP_TICKS + 109) {
                PickupCard.LOGGER.info("[harness-auto] 恢复后: {}", configLabels(mc));
                // 连来三张：三张卡必须三把不同的平滑账（键唯一），y 值各不相同 ——
                // 从前同款卡共用一个身份，全部钉在同一个 y 上（"预览卡片重叠"）
                clickByLabel(mc, I18n.get("pickupcard.config.button.spawn"));
                clickByLabel(mc, I18n.get("pickupcard.config.button.spawn"));
                clickByLabel(mc, I18n.get("pickupcard.config.button.spawn"));
                PickupCard.LOGGER.info("[harness-auto] 连来三张: {}", configState(mc));
                capture(mc, "stage-cards");
                return;
            }
            if (configTicks == WARMUP_TICKS + 110) {
                PickupCard.LOGGER.info("[harness-auto] 舞台停稳后: {}", configState(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 114 || configTicks == WARMUP_TICKS + 117) {
                // 三张卡是同一 tick 放进去的，入场要走 ~0.4s 才走到"看得见"：等两拍再拍，
                // 拍的就是"三张卡三个位置、三把身份"的本尊（重叠 bug 的反面证据）
                PickupCard.LOGGER.info("[harness-auto] 舞台入场中: {}", configState(mc));
                capture(mc, configTicks == WARMUP_TICKS + 114 ? "stage-mid" : "stage-settled");
                return;
            }
            if (configTicks >= WARMUP_TICKS + 118 && configTicks < WARMUP_TICKS + 118 + CYCLE_EVERY * CYCLE_FRAMES
                    && (configTicks - WARMUP_TICKS - 118) % CYCLE_EVERY == 0) {
                // 【为什么要在收工之后还拍一段】用户第 2 条要的是"预览重播设计时间线" ——
                // 而"重播有没有真的发生"只有跨一个周期比像素才答得出来。回到通用页（单卡预览），
                // 连拍一串；周期 4.6s，取 5 张、每张隔 1.2s，必然覆盖到入场 / 停 / 脉冲 / 淡出。
                // 【118 起拍】前面挪进了"舞台三连拍"（+114/+117），连拍起点跟着后移。
                if (configTicks == WARMUP_TICKS + 118) {
                    clickByLabel(mc, I18n.get("pickupcard.config.page.general.name"));
                    PickupCard.LOGGER.info("[harness-auto] 重播连拍：回到通用页看单卡预览");
                }
                capture(mc, "config-cycle" + ((configTicks - WARMUP_TICKS - 118) / CYCLE_EVERY));
                return;
            }
            if (configTicks >= WARMUP_TICKS + 118 + CYCLE_EVERY * CYCLE_FRAMES
                    && configTicks < WARMUP_TICKS + 118 + CYCLE_EVERY * CYCLE_FRAMES + 12) {
                // 【外观页补拍】翻页流程从前只走 通用→布局→动画→过滤，外观页（颜色 chip 那几行）
                // 从来没有被截过图——英文文案在不在框里只有真截一张才算数。
                int look = configTicks - WARMUP_TICKS - 118 - CYCLE_EVERY * CYCLE_FRAMES;
                if (look == 0) {
                    // 【必须先把按标签伪造的悬停松开】过滤页那一步把 forcedHover 停在
                    // "minecraft:cobblestone" 上没松手；不松的话外观页的每一行都不匹配它，
                    // 于是"标签缓动"永远是 0 —— 截图上看不出来，读数里却骗人（第 18 轮实测）。
                    // 这一张要验的正是"停住的指针 → 带子/标签/说明三处指同一行"。
                    hoverByLabel(mc, null);
                    clickByLabel(mc, I18n.get("pickupcard.config.page.look.name"));
                    return;
                }
                if (look == 2) {
                    // 真指针停到「Bar width」那一行：组件列读的是真指针，
                    // 这一张要证明"命中的那一行"和"画出来的那条悬停带"是同一条（判据 1）。
                    pointColumnByLabel(mc, I18n.get("pickupcard.config.row.barWidth.name"));
                    return;
                }
                if (look == 4) {
                    capture(mc, "look");
                    PickupCard.LOGGER.info("[harness-auto] 外观页: {}", configLabels(mc));
                    // 【为什么这一张要把状态也打出来】A-10 起"悬停哪一行"由树回答：
                    // 带子（树自己画）、标签缓动、底部说明三处必须指同一行。
                    // 截图只看得出带子，看不出"缓动有没有跟上、说明是不是这一行"。
                    PickupCard.LOGGER.info("[harness-auto] 外观页状态: {} | {}",
                            configState(mc), columnDump(mc));
                    if (mc.screen instanceof PickupCardConfigScreen screen) {
                        PickupCard.LOGGER.info("[harness-auto] 外观页悬停路由: {}", screen.hoverRouteDump());
                    }
                    return;
                }
                if (look == 5) {
                    // 【A-16：滚动这条链必须在真机上真的走一遍】上面那张是滚动前的样子。
                    // 【为什么滚在这一页】外观页 12 行、可见 10 行，才有溢出可滚；通用页只有 9 行、
                    // 装得下 —— 在那里滚一格是"0 → 0"，读数看着没崩，其实什么都没验
                    // （第 33 轮就是这么发现的：那一步从落地起就没滚起来过）。
                    if (mc.screen instanceof PickupCardConfigScreen screen) {
                        screen.scrollForHarness(-1);
                    }
                    return;
                }
                if (look == 6) {
                    // 隔一帧再读：偏移要等下一帧的布局才落到几何上。
                    if (mc.screen instanceof PickupCardConfigScreen screen) {
                        PickupCard.LOGGER.info("[harness-auto] A-16 滚动命中: {}",
                                screen.scrollHitDump());
                    }
                    capture(mc, "look-scrolled");
                    return;
                }
                if (look == 7) {
                    // ---- A-11b：长按重复（框架推断）----
                    // 【为什么必须真机走这一条】"同一个键没抬又按下 = 重复"这条判据完全建立在
                    // "按下与抬起都真的到得了树"之上；而宿主从前压根没有 keyReleased 覆写
                    // （第 36 轮之前那条链是断的）。探针一口气把 首按 / 不松再来 / 抬起 / 再按
                    // 四步走完 —— 中间不隔帧，见 keyRepeatForHarness 的说明。
                    if (mc.screen instanceof PickupCardConfigScreen screen) {
                        PickupCard.LOGGER.info("[harness-auto] A-11b 长按重复: {}", screen.keyRepeatForHarness());
                    }
                    return;
                }
                if (look == 8) {
                    // 探针之后界面还得是好的：这几下键不该把树弄坏。
                    // 【读数里"焦点行=-1"是对的】这一页从头到尾没点过任何一行，本来就没有焦点控件 ——
                    // 它证明的是"探针没把树弄出焦点"，不是"焦点被键挪走了"（本轮前半段
                    // "Tab 之后 / Shift+Tab 之后"那两条读数才是焦点那件事的证据）。
                    if (mc.screen instanceof PickupCardConfigScreen screen) {
                        PickupCard.LOGGER.info("[harness-auto] A-11b 探针之后的键盘路由: {}", screen.keyboardRouteDump());
                    }
                    capture(mc, "key-repeat-probe");
                    return;
                }
            }
            if (configTicks >= WARMUP_TICKS + 118 + CYCLE_EVERY * CYCLE_FRAMES + 12) {
                PickupCard.LOGGER.info("[harness-auto] 配置界面模式收工，退出客户端");
                mc.stop();
            }
        }

        // ------------------------------------------------------------------
        // 第三个形态：网格（A-19）
        // ------------------------------------------------------------------

        /**
         * 网格形态的无人值守驱动：开屏 → 定妆 → 方向键走 → 走出视口 → 悬停 → 滚一格 → 退出。
         *
         * <p>【为什么每一步都留读数】这一轮的主要产出是「哪里真的缺」的证据，不是界面本身。
         * 三条关键读数：<b>列数</b>（退让）、<b>方向键落点</b>（二维，不是序号 +1）、
         * <b>焦点出视口时的容器偏移</b>（{@code scrollIntoView} 尚不存在的证据）。
         */
        static void tickGrid(Minecraft mc) {
            if (mc.level == null || mc.getOverlay() != null) {
                return;
            }
            // 与 tickConfig 同一条：quickPlay 期间 MC 自己还会换几次界面（收块屏、地形加载屏），
            // 开一次会被它盖掉 —— 所以一直开到它真的挂上，挂上之后再动缩放。
            if (!(mc.screen instanceof CardGridScreen)) {
                mc.setScreen(new CardGridScreen(null));
                return;
            }
            applyGuiScale(mc);
            gridTicks++;
            CardGridScreen screen = (CardGridScreen) mc.screen;

            if (gridTicks == WARMUP_TICKS) {
                mc.setScreen(new CardGridScreen(null));
                PickupCard.LOGGER.info("[harness-auto] 网格界面已打开");
                return;
            }
            if (gridTicks == WARMUP_TICKS + 20) {
                capture(mc, null);
                PickupCard.LOGGER.info("[harness-auto] {}", screen.gridDump());
                PickupCard.LOGGER.info("[harness-auto] {}", screen.paintedDump());
                return;
            }
            if (gridTicks == WARMUP_TICKS + 24) {
                clickCell(screen, 0);
                PickupCard.LOGGER.info("[harness-auto] {}", screen.focusDump());
                return;
            }
            if (gridTicks == WARMUP_TICKS + 26) {
                screen.navForHarness(GLFW.GLFW_KEY_RIGHT);
                screen.navForHarness(GLFW.GLFW_KEY_RIGHT);
                capture(mc, "nav");
                PickupCard.LOGGER.info("[harness-auto] {}", screen.focusDump());
                return;
            }
            if (gridTicks == WARMUP_TICKS + 28) {
                screen.navForHarness(GLFW.GLFW_KEY_DOWN);
                // 【这一条才证明是二维】只走"序号 + 1"的话这里是 行0列3，二维才是 行1列2
                PickupCard.LOGGER.info("[harness-auto] {}", screen.focusDump());
                return;
            }
            if (gridTicks == WARMUP_TICKS + 30) {
                for (int i = 0; i < 4; i++) {
                    screen.navForHarness(GLFW.GLFW_KEY_DOWN);
                }
                capture(mc, "offscreen");
                PickupCard.LOGGER.info("[harness-auto] {}", screen.scrollProbeDump());
                return;
            }
            if (gridTicks == WARMUP_TICKS + 34) {
                screen.pointAtCellForHarness(0);
                capture(mc, "hover");
                return;
            }
            if (gridTicks == WARMUP_TICKS + 36) {
                screen.scrollForHarness(-1.0d);
                return;
            }
            if (gridTicks == WARMUP_TICKS + 38) {
                capture(mc, "scrolled");
                PickupCard.LOGGER.info("[harness-auto] {}", screen.scrollHitDump());
                return;
            }
            if (gridTicks == WARMUP_TICKS + 40) {
                // 【为什么单独来这一下】整格滚动（184）恰好躲开"格子文字糊到标题上"那一段
                // （约 89–123），所以上面那几张产物拍不到裁剪漏掉文字的形态。100 落在那段里，
                // 这一张才是"文字有没有跟着形状一起被裁"的产物证据。
                screen.scrollToForHarness(100f);
                return;
            }
            if (gridTicks >= WARMUP_TICKS + 42) {
                capture(mc, "clip-probe");
                PickupCard.LOGGER.info("[harness-auto] 裁剪探针: 偏移=100（文字该被裁在视口里）");
                PickupCard.LOGGER.info("[harness-auto] 网格模式收工，退出客户端");
                mc.stop();
            }
        }

        /** 点某一格（走界面自己的鼠标路径，不是直接 {@code requestFocus}）。 */
        private static void clickCell(CardGridScreen screen, int index) {
            float[] center = screen.cellCenterForHarness(index);
            screen.mouseClicked(center[0], center[1], 0);
            screen.mouseReleased(center[0], center[1], 0);
        }

        /** 预览重播连拍：每 24 tick（1.2s）一张，5 张覆盖一个 4.6s 周期。 */
        private static final int CYCLE_EVERY = 24;
        private static final int CYCLE_FRAMES = 5;

        /** 三张名单现在的样子 —— 截图看不出"回车到底写没写进配置"，只有这个能。 */
        private static String filterDump(Minecraft mc) {
            return PickupCardConfig.filterDump();
        }

        /** 界面自己的状态读数（哪一页、哪个样例、动画走到哪）—— 截图看不出"动画有没有真播"。 */
        private static String configState(Minecraft mc) {
            return mc.screen instanceof PickupCardConfigScreen screen ? screen.stateDump()
                    : "(不是配置界面)";
        }

        private static String columnDump(Minecraft mc) {
            return mc.screen instanceof PickupCardConfigScreen screen ? screen.columnDump()
                    : "(不是配置界面)";
        }

        /**
         * 键盘路由读数（焦点在哪一行、那个控件叫什么、值是多少）。
         * <p>【为什么这条读数只能从这里出来】"焦点归树"之后，宿主手上<b>没有</b>第二份焦点，
         * 所以"现在谁拿着键盘"这个问题只有树答得出来（见 {@code TrellisColumn.focusedControlRow}）。
         */
        private static String keyboardDump(Minecraft mc) {
            return mc.screen instanceof PickupCardConfigScreen screen ? screen.keyboardRouteDump()
                    : "(不是配置界面)";
        }

        /**
         * 控件自检：屏幕上的控件，这一帧是不是<b>全部</b>都被画过。
         * <p>【为什么这条必须自动报】"控件在、也能点、就是没画"这种 bug 全绿通过：点击有效、
         * 布局数字正确、单测也不管绘制 —— 只有人盯着截图才看得出，而这次真的漏了整整一列
         * （标签列四条标签一个都没画，是复核截图转写文字时发现的）。控件自己记着"这帧画过没有"，
         * 所以这里能一眼报出来。
         */
        private static void checkPainted(Minecraft mc) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return;
            if (screen.paintedCount() == screen.widgetCount()) {
                PickupCard.LOGGER.info("[harness-auto] 控件自检：{} 个控件这一帧全部画过", screen.widgetCount());
            } else {
                PickupCard.LOGGER.error("[harness-auto] 控件自检没过：共 {} 个控件，这一帧只画了 {} 个 —— 有控件漏了绘制调用",
                        screen.widgetCount(), screen.paintedCount());
            }
        }

        /**
         * 按标签找控件、点它的中心，返回是否真的点到。
         * <p>
         * 【为什么不按"屏幕比例"点】第一版写成 {@code height * 1.0}，正好落在按钮下沿之外 ——
         * 点击静默地什么都没发生，日志里只看到"值没变"，跟"功能坏了"长得一模一样。
         * 按标签找 + 用它自己的 bounds，换分辨率、换列宽都不用改，点不中还会现形（找不到就报）。
         */
        private static boolean clickByLabel(Minecraft mc, String label) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return false;
            // 【为什么点不到就得报】控件是自绘的，没有原版 children() 可以找 ——
            // 界面自己知道"哪一行叫什么"，找不到时静默点空 = 看起来像功能坏了。
            if (!screen.clickOption(label)) {
                PickupCard.LOGGER.warn("[harness-auto] 界面上找不到『{}』这个控件（或这一档画布上它没画出来）",
                        label);
                return false;
            }
            return true;
        }

        /** 把"鼠标停在一行上"定住（null = 取消）—— 悬停那一下的读数与截图要靠它。 */
        private static void hoverByLabel(Minecraft mc, String label) {
            if (mc.screen instanceof PickupCardConfigScreen screen) {
                screen.hoverForHarness(label);
            }
        }

        /** 让组件列把指针当作落在某一行控件的中心上（它不吃按标签伪造的悬停）。 */
        private static void pointColumnByLabel(Minecraft mc, String label) {
            if (mc.screen instanceof PickupCardConfigScreen screen
                    && !screen.pointColumnAtForHarness(label)) {
                PickupCard.LOGGER.warn("[harness-auto] 界面上找不到『{}』这一行，列指针没地方放", label);
            }
        }

        /** 拖一下滑条：点击能改值，拖拽能不能改值是两条不同的路径。 */
        private static void dragOption(Minecraft mc, String label, double ratio) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return;
            if (!screen.dragOption(label, ratio)) {
                PickupCard.LOGGER.warn("[harness-auto] 界面上找不到『{}』这个控件", label);
            }
        }

        /**
         * 往当前焦点控件里敲几个字符（<b>不回车</b>）。
         * <p>【为什么不复用 {@code typeByLabel}】它点完就回车提交 —— 而键盘路由要在"字敲进去了、
         * 但还没提交"这一刻读：提交会把编辑态关掉，读数就看不出"焦点还在不在这一行"。
         */
        /**
         * 按一下 Tab（{@code forward=false} 就是 Shift+Tab）。
         *
         * <p>走的是玩家真正走的那条路：{@code Screen.keyPressed} → 树的
         * {@code focusNext} —— 不是直接问树要一个行号，否则验的就不是"键能不能换焦点"。
         */
        private static boolean pressTab(Minecraft mc, boolean forward) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return false;
            screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, forward ? 0 : GLFW.GLFW_MOD_SHIFT);
            return true;
        }

        private static boolean typeChars(Minecraft mc, String text) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return false;
            for (char c : text.toCharArray()) {
                screen.charTyped(c, 0);
            }
            return true;
        }

        /** 往某个文本框打字并回车（点 → 逐字 → 回车提交）。 */
        private static boolean typeByLabel(Minecraft mc, String label, String text) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return false;
            if (!screen.typeOption(label, text)) {
                PickupCard.LOGGER.warn("[harness-auto] 界面上找不到『{}』这个输入框", label);
                return false;
            }
            return true;
        }

        /** 界面上现在有哪些控件（按标签）。日志里留一份 —— 截图看不出"第 2 页到底有没有那几项"。 */
        private static String configLabels(Minecraft mc) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return "(不是配置界面)";
            // 名字 + 位置 + 显示值：布局是算出来的，"框压到边上"得能不靠眼睛查出来。
            return "选项[" + String.join(" | ", screen.optionDump()) + "]";
        }

        /** 切到下一页：清屏 → 注入 → 从头计时。页用完了就收工。 */
        private static void advance(Minecraft mc) {
            page++;
            if (page >= TOTAL_PAGES) {
                PickupCard.LOGGER.info("[harness-auto] 收工，退出客户端");
                mc.stop();
                return;
            }
            CardFixtures.clear();
            if (page == SPIKE_PAGE) {
                screen.setMeasure(false);
                screen.setSpike(true);
                PickupCard.LOGGER.info("[harness-auto] 第 {}/{} 页: 矢量 spike", page + 1, TOTAL_PAGES);
            } else if (page == MEASURE_PAGE) {
                screen.setSpike(false);
                screen.setMeasure(true);
                for (CardFixtures.Fixture fixture : CardFixtures.measure()) {
                    CardFixtures.inject(fixture);
                }
                PickupCard.LOGGER.info("[harness-auto] 第 {}/{} 页: 测量页（纯黑底/无辅助线/无读数）",
                        page + 1, TOTAL_PAGES);
            } else {
                screen.setMeasure(false);
                screen.setSpike(false);
                for (CardFixtures.Fixture fixture : PAGES.get(page)) {
                    CardFixtures.inject(fixture);
                }
                PickupCard.LOGGER.info("[harness-auto] 第 {}/{} 页: {}", page + 1, TOTAL_PAGES,
                        PAGES.get(page).stream().map(CardFixtures.Fixture::label)
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
            sinceInject = 0;
        }

        /**
         * HUD 模式：注入样例，然后**什么都不做**，等 HUD 自己画。
         * <p>
         * 【为什么非要有这么一条】调试屏与 HUD 共用 {@link CardStage#renderInto}，
         * 但**触发路径不同**：一个是 {@code Screen.render}，一个是 Forge 的 GUI 渲染事件。
         * 共用同一个方法不等于共用同一条路径 —— "调试屏里好好的、玩家那边一张卡都没有"
         * 正好就是这个差别，而上面那些自动截图全都走的调试屏那条路，永远测不到。
         */
        /**
         * 开跑前把上一轮的截图删掉。
         * <p>【为什么非删不可】{@code run/screenshots} 只增不删，而连拍的文件名是按 tick 编号的
         * （{@code …-late24}）—— 换了 harness 常数之后，同一批编号会被新一轮覆盖一部分、
         * 又留下一部分旧的，**两轮的帧混在同一个目录里**。分析脚本按文件名收集，于是把上一轮
         * 的帧当成本轮的帧，得出过一个完全错误的结论：上一轮在 tick 15 触发过"救回"，
         * 它那张 {@code late24} 本来就是全不透明的，被读成了"淡到一半突然闪回不透明"。
         * 跨轮混帧是"看不出来的错"里最难查的一种，所以让 harness 自己保证目录是干净的。
         */
        private static void clearOldShots(Minecraft mc) {
            // 【顺带记一句】截图的像素尺寸就是窗口尺寸，而窗口尺寸并非每次都是命令行给的
            // 1280x720 —— 2026-09-18 有一轮拍出来是 848x467，于是所有按 1280x720 写死的
            // 取样区域全部落空（表现是"数字不见了"，其实是找错了地方）。分析脚本要么先断言
            // 尺寸，要么从日志里的画布尺寸反推比例，别假设。
            PickupCard.LOGGER.info("[harness-auto] 截图尺寸：{}x{}（分析脚本别假设 1280x720）",
                    mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
            java.io.File dir = new java.io.File(mc.gameDirectory, "screenshots");
            java.io.File[] old = dir.listFiles((d, n) -> n.startsWith("pickupcard-"));
            if (old == null || old.length == 0) return;
            int n = 0;
            for (java.io.File f : old) {
                if (f.delete()) n++;
            }
            PickupCard.LOGGER.info("[harness-auto] 清掉上一轮的 {} 张截图（跨轮同名会污染分析）", n);
        }

        private static void tickHud(Minecraft mc) {
            if (mc.level == null || mc.getOverlay() != null) {
                return;         // 同上：世界进来了才开始数 tick
            }
            applyGuiScale(mc);
            hudTicks++;
            if (!hudInjected) {
                if (hudTicks < WARMUP_TICKS) return;
                if (mc.getOverlay() != null || mc.screen != null || mc.level == null) return;
                clearOldShots(mc);
                CardFixtures.clear();
                for (CardFixtures.Fixture fixture : PAGES.get(0)) {
                    CardFixtures.inject(fixture);
                }
                PickupCard.LOGGER.info("[harness-auto] HUD 模式：注入 {} 张样例，不开调试屏",
                        PAGES.get(0).size());
                hudInjected = true;
                hudTicks = WARMUP_TICKS;
                return;
            }
            int age = hudTicks - WARMUP_TICKS;

            // 【入场逐帧 trace】入场那 480ms 里每一张卡都开着裁剪（更贵的提交路径 + 每张两次
            // flush），而"入场时好卡"这个用户反馈的落点只可能在这十几帧里。逐帧记下
            // layout/paint/flushes/rise，才能把"贵在哪一帧、随张数怎么长"变成可读的数 ——
            // 只看稳态那一个抽样数，永远看不到这一段。
            if (age <= ENTER_TRACE_TICKS) {
                CardStage.Stats trace = CardStage.INSTANCE.stats();
                PickupCard.LOGGER.info("[harness-auto] 入场逐帧 age={} cards={} layout={}us paint={}us"
                                + " flushes={} rise={}",
                        age, trace.live(), trace.layoutMicros(), trace.paintMicros(), trace.flushes(),
                        String.format(java.util.Locale.ROOT, "%.2f", trace.firstRise()));
            }

            if (age == 14) {
                // 【扫光时窗】入场 480ms（≈10 tick）结束后扫光跑 480ms（≈19 tick 收尾）——
                // age=14 正好在窗中央。扫光是一次性的，常规稳态截图（SHOT_AFTER_OPEN=40 起）
                // 永远拍不到它，这一张就是"扫光真的存在"的证据。
                Screenshot.grab(mc.gameDirectory, "pickupcard-hud-shimmer",
                        mc.getMainRenderTarget(),
                        m -> PickupCard.LOGGER.info("[harness-auto] 截图: pickupcard-hud-shimmer -> {}",
                                m.getString()));
            }

            // 【入场中途的两张】稳态图证明不了动画：镜像卡片"内容从竖条（右）那侧滑出来"
            // 这件事只在展开到一半时可见（2026-09-20 用户报「卡片动画镜像了、文字动画没有」，
            // 就是稳态截图全绿、动画途中才现形的那种）。rise≈0.3 与 ≈0.7 各一张。
            if (age == 3 || age == 7) {
                Screenshot.grab(mc.gameDirectory, "pickupcard-hud-enter" + age,
                        mc.getMainRenderTarget(),
                        m -> PickupCard.LOGGER.info("[harness-auto] 截图: enter{} -> {}",
                                age, m.getString()));
            }

            // 【收工必须是独立卫语句，不能是链尾的 else if】它原来是链尾，而前面
            // `else if (exitSeen)` 一旦成立就把后面所有分支全吞掉 —— 于是"到点退出"永远走不到，
            // 客户端拍完三张图就挂在那儿，用户看到的就是"跑完了窗口还开着、像在等人操作"。
            // 同一条件当时还写了两遍（第一遍是空块），第二遍本就不可达 —— 这类死分支编译器不报，
            // 只在"进程不退出"这种症状上显形。
            if (age >= SHOT_AFTER_OPEN + QUIT_AFTER_SHOT) {
                PickupCard.LOGGER.info("[harness-auto] HUD 模式收工，退出客户端");
                mc.stop();
                return;
            }

            if (age == SHOT_AFTER_OPEN) {
                CardStage.Stats s = CardStage.INSTANCE.stats();
                PickupCard.LOGGER.info("[harness-auto] HUD 读数 cards={} painted={} layout={}us paint={}us"
                                + " flushes={}（本帧原版批次提交次数）",
                        s.live(), s.painted(), s.layoutMicros(), s.paintMicros(), s.flushes());
                // 【峰值为什么要单独打】入场中段那几帧（全部卡都开着裁剪 + 内容正在滑）才是
                // 一帧里最贵的位置，而它跟"稳态抽一帧"不在同一个时刻。峰值是"这一轮卡堆里
                // 最糟的那一帧"，入场动画的性能问题只有它能指认。
                PickupCard.LOGGER.info("[harness-auto] HUD 峰值 worst={}us（cards={} flushes={} {}）"
                                + " 单帧最多提交={} 次 —— 一轮卡堆里的极值（layout+paint / 批次提交）",
                        s.peakMicros(), s.peakCards(), s.peakFlushes(), s.peakShape(), s.maxFlushes());
                // 【性能护栏（2026-09-20 定）】排布是纯数学，超 500us 必是回归；绘制（NanoVG 外壳
                // + 图标每帧现渲 + 文字）稳态一摞卡在毫秒以内，4000us 是数倍余量。超线只报 ERROR
                // 不炸进程——这条线是给日志审查抓的（grep「性能护栏」）。
                // 【峰值也进护栏】稳态抽样过线才算回归是旧尺子；入场中段超线同样是回归，
                // 用更松的 2 倍预算（那一瞬所有卡都在裁剪 + 平移，本来就比稳态贵）。
                if (s.live() >= 4 && (s.layoutMicros() > 500 || s.paintMicros() > 4_000)) {
                    PickupCard.LOGGER.error("[harness-auto] 性能护栏：cards={} layout={}us（预算 500）"
                                    + " paint={}us（预算 4000）—— 渲染路径疑似回归",
                            s.live(), s.layoutMicros(), s.paintMicros());
                }
                if (s.peakCards() >= 4 && s.peakMicros() > 8_000) {
                    PickupCard.LOGGER.error("[harness-auto] 性能护栏（峰值）：worst={}us cards={}"
                                    + " flushes={}（预算 8000）—— 入场/退场中段疑似回归",
                            s.peakMicros(), s.peakCards(), s.peakFlushes());
                }
                logHudSafeZone(mc);
                // 在屏的是哪几张 + <b>每张的实际矩形</b>：「少了我的那张卡」与「位置又不对」是最常见的
                // 两类反馈，而"画在哪"只有从渲染层的 slot 上读才是准的 —— 从截图上量要按颜色挑、
                // 要跟世界纹理分开，量出来还可能是别的元素（2026-09-18 就为了四个 x 绕了半天）。
                PickupCard.LOGGER.info("[harness-auto] HUD 在屏: {}", CardStage.INSTANCE.lastSlots()
                        .stream().map(slot -> String.format(java.util.Locale.ROOT,
                                "%s@(%.0f,%.0f %.0fx%.0f)", slot.view().key(),
                                slot.x(), slot.y(), slot.width(), slot.height()))
                        .collect(java.util.stream.Collectors.joining(", ")));
                Screenshot.grab(mc.gameDirectory, "pickupcard-hud", mc.getMainRenderTarget(),
                        m -> PickupCard.LOGGER.info("[harness-auto] 截图: pickupcard-hud -> {}",
                                m.getString()));
            } else if (age == MERGE_BUMP_AFTER) {
                // 最新那张（= 注入顺序里最后一个）再捡一次：这一次既没退场也没淡回，
                // 屏幕上该出现的是"整张卡鼓一下 + 数字从旧值滚到新值"。
                CardFixtures.Fixture newest = PAGES.get(0).get(PAGES.get(0).size() - 1);
                CardFixtures.inject(newest);
                mergeBumpDone = true;
                PickupCard.LOGGER.info("[harness-auto] 合并探针：再捡一次「{}」（应该鼓一下 + 数字滚动）",
                        newest.label());
            } else if (mergeBumpDone && age > MERGE_BUMP_AFTER
                    && (age - MERGE_BUMP_AFTER - 1) % MERGE_EVERY == 0
                    && (age - MERGE_BUMP_AFTER - 1) / MERGE_EVERY < MERGE_FRAMES) {
                // 合并动画同样按毫秒走，默认 bumpMs=300 只有 6 tick —— 要量就得先把 bumpMs
                // 拉长（跟 exitMs 一个道理，见 EXIT_LATE_* 那段）。
                Screenshot.grab(mc.gameDirectory,
                        "pickupcard-hud-merge" + (age - MERGE_BUMP_AFTER),
                        mc.getMainRenderTarget(),
                        m -> PickupCard.LOGGER.info("[harness-auto] 截图: merge{} -> {}",
                                age - MERGE_BUMP_AFTER, m.getString()));
            } else if (age == EXIT_PUSH_AFTER) {
                // 再推一张（钻石，跟这一页那五件都不是同一样东西）：③a 之后屏满**不再顶掉旧卡**
                // 而是排队 —— 这一步因此从"触发淘汰"变成了"验证排队"。
                CardFixtures.Fixture extra = CardFixtures.all().get(6);
                CardFixtures.inject(extra);
                PickupCard.LOGGER.info("[harness-auto] 推第 6 张（{}）：屏满，应该排队", extra.label());
            } else if (!exitSeen && anyExiting()) {
                // 【为什么不再用固定 tick】退场什么时候发生取决 holdMs / 同屏上限 / 排队上限，
                // 写死 tick 就会在改了配置之后拍空。改成"看见第一帧退场就记录"，之后按帧数推进。
                exitSeen = true;
                ticksSinceExitSeen = 0;
                PickupCard.LOGGER.info("[harness-auto] 看见退场：cards={} painted={}",
                        CardStage.INSTANCE.stats().live(), CardStage.INSTANCE.stats().painted());
            } else if (exitSeen) {
                // 【计数必须在每一 tick 自增】上一版把 ++ 写在"还没拍过"的条件里，拍完就冻住了，
                // 于是后面那个"淡到一半再捡一次"的门槛永远到不了 —— 救回那一步从没跑过。
                ticksSinceExitSeen++;
                if (!exitShotDone && ticksSinceExitSeen == 1) {
                    exitShotDone = true;
                    PickupCard.LOGGER.info("[harness-auto] 退场中读数 cards={} painted={}",
                            CardStage.INSTANCE.stats().live(), CardStage.INSTANCE.stats().painted());
                    Screenshot.grab(mc.gameDirectory, "pickupcard-hud-exit", mc.getMainRenderTarget(),
                            m -> PickupCard.LOGGER.info("[harness-auto] 截图: pickupcard-hud-exit -> {}",
                                    m.getString()));
                } else if (exitSeen && ticksSinceExitSeen >= EXIT_LATE_TICK
                        && (ticksSinceExitSeen - EXIT_LATE_TICK) % EXIT_LATE_EVERY == 0
                        && ticksSinceExitSeen <= EXIT_LATE_TICK + EXIT_LATE_EVERY * (EXIT_LATE_FRAMES - 1)) {
                    // 【连拍见 EXIT_LATE_* 的说明】这组截图只在 exitMs 被拉长之后才有意义；
                    // 默认 480ms 在 20Hz 的 tick 网格上只有 9.6 格，拍出来是空屏。
                    Screenshot.grab(mc.gameDirectory,
                            "pickupcard-hud-exit-late" + ticksSinceExitSeen, mc.getMainRenderTarget(),
                            m -> PickupCard.LOGGER.info("[harness-auto] 截图: exit-late{} -> {}",
                                    ticksSinceExitSeen, m.getString()));
                } else if (exitShotDone && !reviveDone && anyExiting()
                        && ticksSinceExitSeen >= REVIVE_AFTER_EXIT_SEEN) {
                    // 【为什么要推这一下】用户报过「淡出最后一帧图标和文字完全不透明，然后消失」。
                    // 逐帧 alpha 探针证明退场曲线本身是单调的（不再有第二种可能），那剩下的解释
                    // 只有一种：淡出被"救回来"了 —— 合并会把退场撤销（CardView#absorbMerge）。
                    // 这里就在淡出播到一半时再捡一个同样的石头，把它复现出来。
                    reviveDone = true;
                    ticksSinceRevive = 0;
                    CardFixtures.inject(PAGES.get(0).get(0));
                    PickupCard.LOGGER.info("[harness-auto] 淡出中再捡一次石头（同一样例：最老那张就是它）");
                } else if (reviveDone && ++ticksSinceRevive == REVIVE_SHOT_AFTER + 1) {
                    PickupCard.LOGGER.info("[harness-auto] 救回后读数 cards={}",
                            CardStage.INSTANCE.stats().live());
                    Screenshot.grab(mc.gameDirectory, "pickupcard-hud-revive", mc.getMainRenderTarget(),
                            m -> PickupCard.LOGGER.info("[harness-auto] 截图: pickupcard-hud-revive -> {}",
                                    m.getString()));
                }
            }
        }

        /** 产物名跟着页面走：测量页的名字必须一眼看得出是测量页，不是"某张截图"。 */
        private static String shotName(String suffix) {
            // 用 if 而不是 switch：SPIKE_PAGE 是 PAGES.size()，不是编译期常量，
            // 编译不过 —— case 标签要求常量表达式。
            String base;
            if (CONFIG_ONLY) {
                base = "pickupcard-config";
            } else if (GRID_ONLY) {
                // 不写这一支的话产物会落到 pickupcard-harness-p0，而 clearOldShots 又会删掉
                // 所有 pickupcard-* —— 名字对不上分析脚本（且是静默的）。
                base = "pickupcard-grid";
            } else if (page == SPIKE_PAGE) {
                base = "pickupcard-harness-spike";
            } else if (page == MEASURE_PAGE) {
                base = "pickupcard-measure";
            } else {
                base = "pickupcard-harness-p" + (page + 1);
            }
            return base + (suffix == null ? "" : "-" + suffix);
        }

        /**
         * 把"卡堆最低边 vs 原版 HUD 带的顶"写进日志。
         * <p>【为什么要这一行】截图上看不出"差 3px"，而用户报过三次"卡片和物品栏 HUD 重叠" ——
         * 每次都是差那几个像素。数字进日志，才不用靠眼睛判。
         */
        private static void logHudSafeZone(Minecraft mc) {
            float lowestCard = 0f;
            float highestCard = Float.MAX_VALUE;
            for (var slot : CardStage.INSTANCE.lastSlots()) {
                lowestCard = Math.max(lowestCard, slot.y() + slot.height());
                highestCard = Math.min(highestCard, slot.y());
            }
            float hudTop = mc.getWindow().getGuiScaledHeight()
                    - com.niuqu.pickupcard.layout.HudSafeZone.bottomInset();
            // 顶端那一行是给"高缩放下顶出屏幕"立的：卡堆的顶必须 >= 0（画布顶端），
            // 而只报最低边是看不出这件事的 —— 上一版就这么漏过去了。
            PickupCard.LOGGER.info(
                    "[harness-auto] HUD 安全区：卡堆最低边 y={}，HUD 带顶 y={}（缝 {}，底部留白 {}，"
                            + "画布 {}x{}，卡堆顶 y={}，放得下 {} 张，落点 {}）",
                    Math.round(lowestCard), Math.round(hudTop), Math.round(hudTop - lowestCard),
                    com.niuqu.pickupcard.layout.HudSafeZone.bottomInset(),
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    highestCard == Float.MAX_VALUE ? "-" : Math.round(highestCard),
                    com.niuqu.pickupcard.layout.StackLayout.fittingCount(
                            // 底锚之后"放几张"从锚线<b>向上</b>数：锚线就是可用高的全部
                            PickupCardConfig.layoutSnapshot().anchorTop(
                                    mc.getWindow().getGuiScaledHeight(),
                                    CardStage.INSTANCE.previewStyle().boxHeight() * effectiveScale(mc),
                                    com.niuqu.pickupcard.layout.HudSafeZone.bottomInset()),
                            // 用**本帧生效的**卡高与间距算（乘上当前缩放），否则这行日志会
                            // 在缩放档下说"放得下 7 张"而排布实际只放得下 5 张
                            CardStage.INSTANCE.previewStyle().boxHeight() * effectiveScale(mc),
                            PickupCardConfig.layoutSnapshot().separation() * effectiveScale(mc)),
                    // 【为什么缝可以是负的】条带档下卡片横向上已经避开快捷栏，纵向就不必让开它 ——
                    // 那个负数正是"卡片下到快捷栏那一层了"的证据，不是回归。落在哪一档由这行末尾的
                    // [落点] 日志回答（见 CardStage#place）。
                    CardStage.INSTANCE.lastSlots().isEmpty() ? "-"
                            : com.niuqu.pickupcard.render.CardStage.INSTANCE.placementNote());
        }

        /** 这一帧有没有卡正在退场（含淡回）：探针靠它决定"什么时候该拍"。 */
        private static boolean anyExiting() {
            return CardStage.INSTANCE.lastSlots().stream()
                    .anyMatch(slot -> slot.view().exiting() || slot.view().reviving());
        }

        /** 这一帧生效的卡片缩放（跟 {@code CardStage#renderInto} 同一个公式：锚线以上可用高）。 */
        private static float effectiveScale(Minecraft mc) {
            LayoutSettings layout = PickupCardConfig.layoutSnapshot();
            float cardH = CardStage.INSTANCE.previewStyle().boxHeight();
            float anchorTop = layout.anchorTop(mc.getWindow().getGuiScaledHeight(), cardH,
                    com.niuqu.pickupcard.layout.HudSafeZone.bottomInset());
            return layout.scale(
                    // 底锚：卡堆向上长，可用高就是锚线到屏幕顶那一段
                    anchorTop,
                    cardH,
                    Math.max(1, CardStage.INSTANCE.stats().live()),
                    layout.separation());
        }

        private static void capture(Minecraft mc, String suffix) {
            CardStage.Stats stats = CardStage.INSTANCE.stats();
            // 这张读数就是"客观门"里的第一道：卡有没有真的进到绘制阶段、排布花了多久。
            // 它进日志而不是只进截图，是因为数字比像素可靠。
            String keys = CardStage.INSTANCE.lastSlots().stream()
                    .map(slot -> slot.view().key())
                    .collect(java.util.stream.Collectors.joining(", "));
            long now = System.currentTimeMillis();
            String ages = CardStage.INSTANCE.lastSlots().stream()
                    .map(slot -> (now - slot.view().notice().bornAt()) + "ms")
                    .collect(java.util.stream.Collectors.joining(", "));
            PickupCard.LOGGER.info("[harness-auto] 读数 cards={} painted={} layout={}us fps={} guiScale={}",
                    stats.live(), stats.painted(), stats.layoutMicros(), mc.getFps(),
                    mc.getWindow().getGuiScale());
            PickupCard.LOGGER.info("[harness-auto] 主题 enterMs={} 最新卡展开进度={} 各卡卡龄=[{}]",
                    stats.enterMs(), String.format(java.util.Locale.ROOT, "%.2f", stats.firstRise()), ages);
            // 活下来的是哪几张：淘汰顺序不能靠推断，得看数据
            PickupCard.LOGGER.info("[harness-auto] 在屏: {}", keys);
            logHudSafeZone(mc);
            String name = shotName(suffix);
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    message -> PickupCard.LOGGER.info("[harness-auto] 截图: {} -> {}", name, message.getString()));
        }
    }
}
