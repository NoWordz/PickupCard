package com.niuqu.pickupcard.magnet;

/**
 * 磁铁检测的纯数学半边：从一次实体数据同步的前后数量里算出"被吸走了几个"。
 * <p>
 * 【与 MC 的关系】这半边不碰任何 MC 类 —— mixin 半边（layers 层）负责拿到
 * before/after 两个 int，这里负责判断该不该弹、弹几个。离线可测（MagnetMathTest）。
 * <p>
 * 【判据】只有「旧多新少」算吸取。变多与不变一律 0 —— 宁可漏报不可误报，
 * 弹一张不该弹的卡比少弹一张烦得多（用户验收口径）。
 */
public final class MagnetMath {
    private MagnetMath() {}

    /** 一次数据同步里"被吸走"的数量：旧多新少才算（新多/不变=不是吸取）。 */
    public static int absorbed(int before, int after) {
        return before > 0 && after >= 0 && after < before ? before - after : 0;
    }
}
