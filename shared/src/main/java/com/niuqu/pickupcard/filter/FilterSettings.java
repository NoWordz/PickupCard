package com.niuqu.pickupcard.filter;

import java.util.List;

/**
 * 过滤三表的配置快照。列表里是未解析的原始规则（见 {@link FilterRule} 的三种写法），
 * 解析发生在每次判定时——拾取是稀疏事件，规则通常不到几十条，不值得做缓存失效。
 *
 * @param blacklist        黑名单：命中则不弹卡
 * @param whitelist        白名单：命中则永远弹卡并强调（优先于黑名单）
 * @param muteList         静音名单：命中照常弹卡，但不强调，且原版拾取音被压掉
 * <p>
 * 【为什么没有"内置忽略表"这个字段了】见 {@link FilterRules} 的类注释：默认不丢任何拾取。
 */
public record FilterSettings(List<String> blacklist,
                            List<String> whitelist,
                            List<String> muteList) {

    public static FilterSettings defaults() {
        return new FilterSettings(List.of(), List.of(), List.of());
    }

    public FilterSettings {
        blacklist = List.copyOf(blacklist);
        whitelist = List.copyOf(whitelist);
        muteList = List.copyOf(muteList);
    }
}
