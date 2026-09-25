package com.niuqu.pickupcard.notice;

/**
 * 同屏放满之后再捡到新东西怎么办。
 * <p>【默认为什么是 REPLACE】卡片的意义是"刚才捡了什么"，最新的那一手永远值得立刻看；
 * 让它去排队等于把最新的信息藏到最后。QUEUE 档留给喜欢"一个都不漏"节奏的玩家。
 */
public enum FullPolicy {
    /** 顶掉在屏最老的一张正常卡（不是溢出卡），新卡立刻入场。 */
    REPLACE,
    /** 旧行为：排到队尾等位子（先来先上屏），队满并进溢出卡或丢弃。 */
    QUEUE
}
