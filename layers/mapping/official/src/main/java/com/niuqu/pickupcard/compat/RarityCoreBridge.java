package com.niuqu.pickupcard.compat;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.rarity.LinkedRarity;
import com.niuqu.pickupcard.rarity.RarityAccent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * RarityCore 联动桥：<b>反射接它的公共 API，不引入编译期依赖</b>。
 *
 * <p>【为什么反射】RarityCore 是可选环境：本仓的 CI 与开发机都没有它的 jar，编译期引依赖
 * 直接断构建；反射的失败模式也最干净——解析失败 = 永远回 vanilla 主题色，行为与没装一样。
 * Forge 与 NeoForge 提供 RarityCoreAPI。Fabric 1.20.1 使用自己的注册表、服务端同步数据和颜色表。
 *
 * <p>【为什么懒解析】构造期 RarityCore 可能还没把自己的配置/注册表装完（mod 构造顺序
 * 不由我们定）；第一次真的要问档位（玩家捡起第一件东西，进世界之后）时它的世界早就稳了。
 * {@link #install(boolean)} 只做"装没装"的判断与注入。
 *
 * <p>【解除方式】不注入即完整解除：{@code RarityAccent} 回落 vanilla 四档主题映射，
 * 渲染路径没有任何一行知道 RarityCore 存在过。
 */
public final class RarityCoreBridge implements LinkedRarity {

    /** RarityCore 的档位范围（其 API 常量 MIN/MAX_RARITY 的本地副本——反射拿常量不如写死一对此值）。 */
    private static final int MIN_TIER = 1;
    private static final int MAX_TIER = 7;

    private static final RarityCoreBridge INSTANCE = new RarityCoreBridge();

    private static volatile boolean resolved;
    private static MethodHandle getRarity;
    private static MethodHandle getColor;
    private static FabricSync fabricSync;

    private record FabricSync(MethodHandle getRarity, MethodHandle clear) {
    }

    private RarityCoreBridge() {
    }

    /** 装了 RarityCore 才注入；没装时什么都不发生（vanilla 主题色就是兜底）。 */
    public static void install(boolean present) {
        if (!present) {
            PickupCard.LOGGER.info("[联动] 未安装 RarityCore，稀有度色走主题（无联动）");
            return;
        }
        RarityAccent.setLinked(INSTANCE);
        PickupCard.LOGGER.info("[联动] 检测到 RarityCore：档位与强调色由它接管，首次使用时懒解析");
    }

    /**
     * 进世界时提前解析，别把它记在第一次拾取那一帧上。
     * <p>【懒解析的代价】{@code Class.forName + unreflect} 本身是微秒级，但它落在
     * “第一次拾取那一帧”里就和 NanoVG 初始化叠在一起了（用户实例实测：那一刻
     * {@code [落点] → [nvg 建立] → [桥已就绪]} 三行挤在 54ms 里）。进世界时先解析掉，
     * 拾取那一帧就只剩画卡的事。解析失败与懒路径完全同款（解联回落主题色）。
     */
    public static void warmUp(boolean present) {
        if (!present) {
            return;
        }
        ensureResolved();
    }

    /** Fabric 在新连接接收同步数据前清掉上一个服务器的档位。 */
    public static void clearSyncedRarities(boolean present) {
        if (!present || !ensureResolved() || fabricSync == null) {
            return;
        }
        try {
            fabricSync.clear().invokeExact();
        } catch (Throwable t) {
            bridgeFailed(t);
        }
    }

    @Override
    public int tierOf(ItemStack stack) {
        if (stack.isEmpty() || !ensureResolved()) {
            return 0;
        }
        try {
            int tier = lookupTier(stack);
            return tier >= MIN_TIER && tier <= MAX_TIER ? tier : 0;
        } catch (Throwable t) {
            return bridgeFailed(t);
        }
    }

    private static int lookupTier(ItemStack stack) throws Throwable {
        if (fabricSync != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() != null && client.getSingleplayerServer() == null) {
                // 服务端发送完整表。缺项由它的客户端接口返回普通档。
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                return (int) fabricSync.getRarity().invokeExact(itemId);
            }
        }
        return (int) getRarity.invokeExact(stack);
    }

    @Override
    public int colorOf(int tier) {
        try {
            return 0xFF000000 | ((int) getColor.invokeExact(tier) & 0xFFFFFF);
        } catch (Throwable t) {
            return bridgeFailed(t);
        }
    }

    @Override
    public int showcaseFrom() {
        // RC 七档：common / uncommon / rare / epic / legendary / mythical / unique。
        // legendary(5) 起才配得上"捡到好东西了"的一次性扫光——vanilla 的 rare(3) 在
        // 七档制里只是中档，跟着 3 走会让 RC 玩家觉得特效廉价。
        return 5;
    }

    /** 缓存已解析的入口。桥失效时解除联动，避免渲染路径反复解析。 */
    private static boolean ensureResolved() {
        if (resolved) {
            return getRarity != null;
        }
        synchronized (RarityCoreBridge.class) {
            if (resolved) {
                return getRarity != null;
            }
            try {
                resolveMethods();
                PickupCard.LOGGER.info("[联动] RarityCore 桥已就绪（{}）",
                        fabricSync == null ? "公共 API" : "Fabric 1.20.1");
            } catch (Throwable t) {
                PickupCard.LOGGER.warn("[联动] RarityCore 桥解析失败，稀有度色回落主题", t);
                getRarity = null;
                RarityAccent.setLinked(null);
            } finally {
                resolved = true;
            }
            return getRarity != null;
        }
    }

    private static void resolveMethods() throws ReflectiveOperationException {
        Class<?> api;
        try {
            api = Class.forName("org.yanbwe.raritycore.api.RarityCoreAPI");
        } catch (ClassNotFoundException ignored) {
            Class<?> registry = Class.forName("org.yanbwe.raritycore.registry.RarityRegistry");
            Class<?> client = Class.forName("org.yanbwe.raritycore.client.RaritycoreClient");
            Class<?> clientData = Class.forName("org.yanbwe.raritycore.client.RarityClientData");
            getRarity = publicMethod(registry, "getRarity", ItemStack.class);
            getColor = publicMethod(client, "getRarityColor", int.class);
            fabricSync = new FabricSync(publicMethod(clientData, "getRarity", ResourceLocation.class),
                    publicMethod(clientData, "clearCache"));
            return;
        }
        getRarity = publicMethod(api, "getRarity", ItemStack.class);
        getColor = publicMethod(api, "getRarityColor", int.class);
    }

    private static MethodHandle publicMethod(Class<?> type, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        return MethodHandles.publicLookup().unreflect(type.getMethod(name, parameters));
    }

    /** 桥在使用中炸了：解除联动并回落，别让一张卡的颜色把渲染帧掀了。 */
    private static int bridgeFailed(Throwable t) {
        PickupCard.LOGGER.warn("[联动] RarityCore 调用失败，回落主题色", t);
        RarityAccent.setLinked(null);
        getRarity = null;
        return 0;
    }
}
