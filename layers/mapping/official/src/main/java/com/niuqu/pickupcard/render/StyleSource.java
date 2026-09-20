package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.style.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * 主题文件的读取与热重读。
 * <p>
 * 【为什么单独成类】它是渲染层里唯一碰资源系统的地方。把它摘出去之后，
 * {@link CardStage} 与 painter 都只认 {@link StyleModel} 这个值，测试与替换都不需要
 * 一个跑着的 Minecraft —— 上一版把这段 try/catch 塞在 436 行的渲染器末尾，
 * 结果是"主题读坏了"与"画错了"混在同一个类的日志里，分不出是谁的锅。
 * <p>
 * 【坏了不抛】主题是玩家可以手改的文件，写坏了不该让拾取提示整个消失 ——
 * 解析失败一律回退整套默认值。
 */
public final class StyleSource {

    /** 主题从哪来：平台侧注入（配置里选的那一项）。默认深色，所以渲染层自己也能跑。 */
    private Supplier<Theme> themeSource = () -> Theme.DARK;
    /** 玩家的个人改动：平台侧注入。默认一项都没改 = 完全用主题。 */
    private Supplier<StyleOverrides> overrideSource = StyleOverrides::none;

    /** 重读间隔：改 JSON 一秒内生效，又不用每帧都去问资源管理器。 */
    private static final long RECHECK_MS = 1_000L;


    private StyleModel cached;
    private Theme cachedTheme;
    private StyleOverrides cachedOverrides;
    private long nextCheckAt;

    public void setThemeSource(Supplier<Theme> source) {
        this.themeSource = source == null ? () -> Theme.DARK : source;
        invalidate();
    }

    public void setOverrideSource(Supplier<StyleOverrides> source) {
        this.overrideSource = source == null ? StyleOverrides::none : source;
        invalidate();
    }

    /** 拿当前设计：主题 + 玩家改动。未到重读点时直接返回缓存。 */
    public StyleModel current(long now) {
        Theme theme = themeSource.get();
        StyleOverrides overrides = overrideSource.get();
        if (cached != null && theme == cachedTheme && overrides.equals(cachedOverrides) && now < nextCheckAt) {
            return cached;
        }
        nextCheckAt = now + RECHECK_MS;
        cachedTheme = theme;
        cachedOverrides = overrides;
        // 【顺序】主题是默认值，改动只覆盖它改过的项，最后统一夹逼。
        // 【夹逼在这里做掉、结果进缓存】玩家手写的数字在"读进来那一刻"就定死了，帧内不会再变，
        // 所以调用方不必每帧再夹一次 —— 从前那是一帧白扔一个 22 分量的 record（2026-09-20 性能轮）。
        // 这一道也覆盖了"主题读坏 → 回退 defaults()"那条路：出门的值一律夹过。
        cached = overrides.apply(read(theme)).sanitized();
        return cached;
    }

    /** 强制下次访问时重读（换世界、资源包重载后调用）。 */
    public void invalidate() {
        cached = null;
        nextCheckAt = 0L;
    }

    private static StyleModel read(Theme theme) {
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(new ResourceLocation("pickupcard", theme.assetPath()));
            if (resource.isEmpty()) {
                return StyleModel.defaults();
            }
            try (var in = resource.get().open()) {
                return StyleModel.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return StyleModel.defaults();
        }
    }
}
