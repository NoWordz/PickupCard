package com.niuqu.pickupcard.compat;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.rarity.LinkedRarity;
import com.niuqu.pickupcard.rarity.RarityAccent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * RarityCore 联动桥：<b>反射接它的公共 API，不引入编译期依赖</b>。
 *
 * <p>【为什么反射】RarityCore 是可选环境：本仓的 CI 与开发机都没有它的 jar，编译期引依赖
 * 直接断构建；反射的失败模式也最干净——解析失败 = 永远回 vanilla 主题色，行为与没装一样。
 * 对面是稳定的静态门面（{@code org.yanbwe.raritycore.api.RarityCoreAPI}，1.20.1 v13.4 实测），
 * 两个签名各一个 MethodHandle，缓存在静态字段。
 *
 * <p>【为什么懒解析】构造期 RarityCore 可能还没把自己的配置/注册表装完（mod 构造顺序
 * 不由我们定）；第一次真的要问档位（玩家捡起第一件东西，进世界之后）时它的世界早就稳了。
 * {@link #install()} 只做"装没装"的判断与注入。
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
    private static MethodHandle getRarity;   // (ItemStack) -> int，1~7 / 0=无档位
    private static MethodHandle getColor;    // (int) -> int，RGB

    private RarityCoreBridge() {
    }

    /** 装了 RarityCore 才注入；没装时什么都不发生（vanilla 主题色就是兜底）。 */
    public static void install() {
        if (!ModList.get().isLoaded("raritycore")) {
            PickupCard.LOGGER.info("[联动] 未安装 RarityCore，稀有度色走主题（无联动）");
            return;
        }
        RarityAccent.setLinked(INSTANCE);
        PickupCard.LOGGER.info("[联动] 检测到 RarityCore：档位与强调色由它接管，首次使用时懒解析");
    }

    @Override
    public int tierOf(ItemStack stack) {
        if (stack.isEmpty() || !ensureResolved()) {
            return 0;
        }
        try {
            int tier = (int) getRarity.invokeExact(stack);
            return tier >= MIN_TIER && tier <= MAX_TIER ? tier : 0;
        } catch (Throwable t) {
            return bridgeFailed(t);
        }
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

    /** 第一次使用时解析两个 MethodHandle；失败过就不再试（每帧渲染路径上不许反复抛类装载）。 */
    private static boolean ensureResolved() {
        if (resolved) {
            return true;
        }
        synchronized (RarityCoreBridge.class) {
            if (resolved) {
                return true;
            }
            try {
                Class<?> api = Class.forName("org.yanbwe.raritycore.api.RarityCoreAPI");
                getRarity = MethodHandles.lookup()
                        .unreflect(api.getMethod("getRarity", ItemStack.class));
                getColor = MethodHandles.lookup()
                        .unreflect(api.getMethod("getRarityColor", int.class));
                resolved = true;
                PickupCard.LOGGER.info("[联动] RarityCore 桥已就绪（7 档 + 玩家自定义色）");
                return true;
            } catch (Throwable t) {
                PickupCard.LOGGER.warn("[联动] RarityCore 桥解析失败，稀有度色回落主题", t);
                RarityAccent.setLinked(null);
                return false;
            }
        }
    }

    /** 桥在使用中炸了：解除联动并回落，别让一张卡的颜色把渲染帧掀了。 */
    private static int bridgeFailed(Throwable t) {
        PickupCard.LOGGER.warn("[联动] RarityCore 调用失败，回落主题色", t);
        RarityAccent.setLinked(null);
        resolved = false;
        return 0;
    }
}
