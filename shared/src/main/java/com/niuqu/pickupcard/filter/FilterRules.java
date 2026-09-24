package com.niuqu.pickupcard.filter;

import java.util.List;

/**
 * 过滤判定的全部规则，纯函数：给一个 {@link FilterSubject} 和三表配置，回答这条拾取
 * 的命运。没有 MC、没有配置文件、没有时钟，所以"白名单压过黑名单"这类优先级问题
 * 都被测试钉死，不靠手感。
 *
 * <p>【默认什么都不丢】这里曾经有一张"内置忽略表"（泥土/圆石/沙子一类），默认开启、
 * 命中即丢弃。它的本意是别让挖一片海滩刷满屏幕，实际效果是：玩家捡了沙子，屏幕上一片
 * 安静、日志里一个字也没有 —— 和"mod 坏了"完全分不出来（这是实测踩到的）。
 * 现在没有默认忽略表：**默认每一次拾取都会弹卡**，想安静由玩家自己往黑名单里写。
 * "不替他决定该看见什么"比"替他省掉几张卡"重要。
 *
 * <p>优先级（从上往下短路）：
 * <ol>
 *   <li>白名单 → 永远弹卡 + 强调</li>
 *   <li>黑名单 → 丢弃</li>
 *   <li>静音名单 → 照常弹卡，但静音</li>
 *   <li>都不中 → 普通弹卡</li>
 * </ol>
 * 白名单与静音名单是正交的：同时在两表里的物品会"强调地静音弹卡"——玩家如果显式
 * 静音了某件白名单物品，那是更晚、更明确的意图，应该赢。
 */
public final class FilterRules {

    /**
     * 一条拾取的判定结果。
     *
     * @param show       弹不弹卡
     * @param emphasized 是否强调（白名单命中：描边/发光增强）
     * @param muted      是否压掉这条拾取的<b>原版</b>拾取音（静音名单命中：卡片照常弹，
     *                   只是不强调、也没有那一声"叮"）。这个 mod 自己不出声，所以"声音"
     *                   指的一直是原版那一下。
     */
    public record Decision(boolean show, boolean emphasized, boolean muted) {
        public static final Decision PLAIN = new Decision(true, false, false);
        public static final Decision DROP = new Decision(false, false, false);
    }

    private FilterRules() {
    }

    public static Decision check(FilterSubject subject, FilterSettings settings) {
        if (anyMatch(subject, settings.whitelist())) {
            return new Decision(true, true, anyMatch(subject, settings.muteList()));
        }
        if (anyMatch(subject, settings.blacklist())) return Decision.DROP;
        if (anyMatch(subject, settings.muteList())) return new Decision(true, false, true);
        return Decision.PLAIN;
    }

    /** 坏规则静默跳过（见 FilterRule.parse），一条拼错的规则不能废掉整张表。 */
    private static boolean anyMatch(FilterSubject subject, List<String> rawRules) {
        for (String raw : rawRules) {
            FilterRule rule = FilterRule.parse(raw);
            if (rule != null && rule.matches(subject)) return true;
        }
        return false;
    }
}
