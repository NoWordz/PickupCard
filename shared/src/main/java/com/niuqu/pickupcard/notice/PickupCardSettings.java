package com.niuqu.pickupcard.notice;

import com.niuqu.pickupcard.text.CountFormat;
import com.niuqu.pickupcard.text.CountMode;

/**
 * 一次会话里所有卡共用的参数。
 * <p>
 * 【为什么是 record 而不是直接读配置】渲染与队列都只认它，于是这两层可以在没有 Forge、
 * 没有配置文件的情况下跑单测；接配置只在平台侧做一次采样。上一版把 config 直接读进渲染
 * 路径，结果每个测试都得先起一个 Forge 环境。
 *
 * @param holdMs        一张卡在屏上停留多久（从最近一次被刷新算起）
 * @param exitMs        退场动画时长；渲染层要等它播完才把这张卡从屏上撤掉
 * @param mergeMode     哪些拾取算同一件东西（同名同 NBT / 同名 / 同名但改名的不并 / 从不合并）
 *                      四档见 {@link MergeMode}
 * @param maxOnScreen   同时在屏上限；满了之后怎么办看 {@link FullPolicy}
 * @param queueSize     排队上限；0 = 不排队（超出的直接丢）
 * @param countFormat   数量怎么写
 * @param enabled       总开关。关掉之后捡东西不再弹卡（屏上已有的也立刻清掉）
 * @param showItemName  显示物品名。关掉只剩"竖条 + 图标 + 数量" —— 卡会明显变窄
 * @param showItemId    显示物品 ID（{@code minecraft:stone}）而不是它的名字
 * @param nameMaxWidth  物品名最大宽度（像素）；0 = 按屏宽比例自动（{@code CardMetrics}）
 * @param fullPolicy    屏满之后的新拾取怎么办（顶掉最老 / 排队），见 {@link FullPolicy}
 * @param countMode     卡上数字回答哪个问题（本次拾取数 / 背包持有总数），见 {@link CountMode}
 */
public record PickupCardSettings(long holdMs,
                                 long exitMs,
                                 MergeMode mergeMode,
                                 int maxOnScreen,
                                 int queueSize,
                                 CountFormat countFormat,
                                 boolean enabled,
                                 boolean showItemName,
                                 boolean showItemId,
                                 int nameMaxWidth,
                                 FullPolicy fullPolicy,
                                 CountMode countMode) {

    public static PickupCardSettings defaults() {
        return new PickupCardSettings(2_600L, 320L, MergeMode.defaults(), 5, 9, CountFormat.PLUS,
                true, true, false, 0, FullPolicy.REPLACE, CountMode.PICKUP);
    }

    /** 旧 11 参签名：{@code countMode} 缺省 {@link CountMode#PICKUP}（本次拾取数），旧调用点零改动。 */
    public PickupCardSettings(long holdMs,
                              long exitMs,
                              MergeMode mergeMode,
                              int maxOnScreen,
                              int queueSize,
                              CountFormat countFormat,
                              boolean enabled,
                              boolean showItemName,
                              boolean showItemId,
                              int nameMaxWidth,
                              FullPolicy fullPolicy) {
        this(holdMs, exitMs, mergeMode, maxOnScreen, queueSize, countFormat, enabled,
                showItemName, showItemId, nameMaxWidth, fullPolicy, CountMode.PICKUP);
    }

    /** 旧 10 参签名：{@code fullPolicy} 缺省 {@link FullPolicy#REPLACE}（0.2.3 起的新默认），旧调用点零改动。 */
    public PickupCardSettings(long holdMs,
                              long exitMs,
                              MergeMode mergeMode,
                              int maxOnScreen,
                              int queueSize,
                              CountFormat countFormat,
                              boolean enabled,
                              boolean showItemName,
                              boolean showItemId,
                              int nameMaxWidth) {
        this(holdMs, exitMs, mergeMode, maxOnScreen, queueSize, countFormat, enabled,
                showItemName, showItemId, nameMaxWidth, FullPolicy.REPLACE);
    }

    /** 把外部来的值夹到合法区间：config 是玩家可改的，非法值不该变成崩溃或永不离场。 */
    public PickupCardSettings sanitized() {
        return new PickupCardSettings(
                Math.max(200L, holdMs),
                Math.max(0L, exitMs),
                mergeMode == null ? MergeMode.defaults() : mergeMode,
                Math.max(1, maxOnScreen),
                Math.max(0, Math.min(128, queueSize)),
                countFormat == null ? CountFormat.PLUS : countFormat,
                enabled,
                showItemName,
                showItemId,
                // 0 = 自动；给了正数就别小于 24px —— 比一个字符还窄的"最大宽度"不是设置，是 bug
                nameMaxWidth <= 0 ? 0 : Math.max(24, Math.min(600, nameMaxWidth)),
                fullPolicy == null ? FullPolicy.REPLACE : fullPolicy,
                countMode == null ? CountMode.PICKUP : countMode);
    }
}
