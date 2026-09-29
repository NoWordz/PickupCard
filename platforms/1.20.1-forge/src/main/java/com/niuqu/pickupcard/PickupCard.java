package com.niuqu.pickupcard;

import com.niuqu.pickupcard.config.PickupCardConfig;
import com.niuqu.pickupcard.compat.RarityCoreBridge;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.PickupRelay;
import com.niuqu.pickupcard.dev.DevHarness;
import com.niuqu.pickupcard.client.PickupCardKeys;
import com.niuqu.pickupcard.config.CardGridScreen;
import com.niuqu.pickupcard.config.PickupCardConfigScreen;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.pickup.Inbox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pickup Card 的 Forge 入口。
 * <p>
 * 【这个 mod 是纯客户端的】它不注册任何方块/物品/网络包，只在客户端嗅探原版的拾取包，
 * 把结果记进账本（{@link Inbox}），渲染层每 tick 来取事件画卡。服务端不用装，任何
 * 服务器都能用。
 * <p>
 * 【为什么加载期不做渲染的事】账本与渲染都是懒活的：第一次真的捡到东西才有卡可画。
 * 玩家如果整局没捡东西，渲染路径一行都不会跑。
 */
@Mod(PickupCard.MOD_ID)
public final class PickupCard {

    public static final String MOD_ID = "pickupcard";

    /** 已经报过"被丢弃"的物品 id，避免连捡一路圆石把日志刷爆。 */
    private static final Set<String> REPORTED_DROPS = ConcurrentHashMap.newKeySet();

    /**
     * 一条拾取被过滤器丢掉了 —— 日志里说清楚是谁干的、怎么放行。
     * <p>
     * 【为什么这条日志必须有】默认已经什么都不丢了，所以这条只在玩家自己写了黑名单时才出现。
     * 但它仍然必须有：被丢掉的拾取在玩家那边就是"什么都没发生"，和"mod 坏了"长得一模一样 ——
     * 实测真有人捡了一路沙子来问这个（那时还是内置表在丢），而当时日志里一个字都没有。
     */
    private static void reportDroppedPickup(CardContent.Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
        if (!REPORTED_DROPS.add(id)) {
            return;
        }
        LOGGER.info("拾取 {} 没有弹卡：命中了你写的黑名单。", id);
        LOGGER.info("  黑名单现在是空的时候不该出现这条 —— 出现了就说明你在 config/pickupcard-client.toml");
        LOGGER.info("  的 [filter] blacklist 里写了它。删掉那条规则，或者把它加进 whitelist（优先级最高）。");
        LOGGER.info("  这条每个物品只报一次。");
    }
    public static final Logger LOGGER = LoggerFactory.getLogger("PickupCard");

    public PickupCard(FMLJavaModLoadingContext context) {
        // 纯客户端 mod：专服上什么都不做，避免有人在服务端装了个客户端 mod 时抛一堆
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOGGER.info("Pickup Card 是客户端 mod，服务端不做任何事");
            return;
        }

        PickupCardConfig.register(context);

        // mod 列表的「配置」按钮（原版 Forge 读同一个扩展点画按钮）与 Configured 等配置
        // 模组的入口都走这一处。**必须自己注册**：不注册时 Configured 会替我们注册它家的
        // 通用编辑器（实测日志 "Registering config factory for mod pickupcard"），玩家点开
        // 看到的就不是本 mod 的卡片界面；而它的源码（multiloader/1.20.1
        // ClientConfigured.java:46-48）明写「已有自定义 factory 的 mod 直接跳过」——
        // 我们先注册，它就让路。关闭时回父界面（PickupCardConfigScreen#onClose 已带）。
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) ->
                        new PickupCardConfigScreen(parent)));

        // 配置是懒采样的：每次真的要用时才读，玩家改完配置不用重启
        Inbox.INSTANCE.setSources(PickupCardConfig::snapshot, PickupCardConfig::filterSnapshot);
        // 被过滤器丢掉的拾取在玩家那边就是"什么都没发生"。接上日志，每个物品只报一次。
        Inbox.INSTANCE.setDropReporter(PickupCard::reportDroppedPickup);
        CardStage.INSTANCE.setLayoutSource(PickupCardConfig::layoutSnapshot);
        // 主题与外观改动也来自配置：主题给默认值，[style] 段只覆盖玩家改过的项
        CardStage.INSTANCE.setStyleSources(PickupCardConfig::theme, PickupCardConfig::styleOverrides);

        // 稀有度联动：装了 RarityCore 就让它的档位与玩家自定义色接管竖条/微光（懒解析，
        // 见 RarityCoreBridge；没装或解析失败都完整回落主题色）
        RarityCoreBridge.install();

        MinecraftForge.EVENT_BUS.register(ClientLifecycle.class);
        MinecraftForge.EVENT_BUS.register(CardStage.INSTANCE);
        // 常驻 HUD 面板（A-24）。**注册在 CardStage 之后**：两个 `RenderGuiEvent.Post` 处理器的
        // 先后 = 谁的 NanoVG 帧后画 = 谁在上面。⚠️ Forge 的派发顺序本喵**没有实测** ——
        // 两个面板今天在屏幕上不重叠（面板在左上、卡堆在下方），所以这一条此刻不承重；
        // 真出现重叠时要先量再定。（不确定的事就写清楚"不确定"。）

        // 调试屏只活在开发环境：正式 jar 里这些类存在，但注册路径根本不会走到
        if (!FMLEnvironment.production) {
            DevHarness.register(context.getModEventBus());
            // 打这一行是为了"它到底注册上没有"在日志里可查——静默注册失败过一次的话，
            // 症状只是"按 F9 没反应"，没有任何线索
            LOGGER.info("调试屏已启用：F9 或 /pickupcarddev");
        }
    }

    /**
     * 客户端生命周期：断线/换世界时把账本清干净。每 tick 的推进与 HUD 渲染都归
     * {@link CardStage} 管（事件消费与绘制必须在同一处才不会错位）。
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static final class ClientLifecycle {

        @SubscribeEvent
        static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            // 磁铁押注对账：吸收信号押 3 tick 等背包增量确认，这里每 tick 放行/丢弃（20Hz）
            PickupRelay.onClientTick(Minecraft.getInstance().player);
            while (PickupCardKeys.CONFIG.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                // 只在没有别的界面时打开：否则会把玩家正在用的界面（比如背包）压掉
                if (mc.screen == null) {
                    mc.setScreen(new PickupCardConfigScreen(null));
                }
            }
            // 卡片一览网格（A-19 第三个形态试点）。同一道门：没有别的界面时才开。
            while (PickupCardKeys.GRID.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    mc.setScreen(new CardGridScreen(null));
                }
            }
        }

        @SubscribeEvent
        static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            // 进世界：把联动桥解析掉。懒解析本来落在"第一次拾取那一帧"上，和 NanoVG 初始化
            // 叠在一起（实测那一帧 51ms）—— 挪到进世界时付，代价是微秒级。
            RarityCoreBridge.warmUp();
        }

        @SubscribeEvent
        static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // 离开世界：队列、NEW 账本、未取走的事件一起清。NEW 不落盘是刻意的，这里就是"忘记"的时机。
            Inbox.INSTANCE.reset();
            CardStage.INSTANCE.clear();
        }
    }
}
