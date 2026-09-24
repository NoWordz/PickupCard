package com.niuqu.pickupcard.pickup;

import com.niuqu.pickupcard.filter.FilterRules;
import com.niuqu.pickupcard.filter.FilterSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Inbox#offer} 交出去的判定，就是放音那侧要读的那个。
 *
 * <p>【为什么值得钉】这个返回值是"静音名单要不要压原版拾取音"的唯一出口，而它的坏法是
 * <b>静默</b>的：判定算对了、卡也弹对了，只是没带出来（这正是它上一次的形态 ——
 * 只有黑名单那一支带了返回值，静音那一支落到 `return false`）。画面、日志、全部单测
 * 都不会红，只有耳朵能发现"该没声的还有声"。
 *
 * <p>【为什么这里只有经验卡】物品那条路要先 {@code BuiltInRegistries}（→ 得
 * {@code Bootstrap.bootStrap()}，实测在纯 JVM 里直接抛），所以 <b>物品的静音判定没法离线跑</b>：
 * 它靠 {@code FilterRulesTest} 钉判定、靠真机/评审钉接线。经验卡不碰注册表，能在纯 JVM 里
 * 走完整条 {@code offer}，所以拿它钉住"返回值是一个真实的判定、不是某个常量"。
 *
 * <p>【这条测试的诚实边界 —— 别高估它】它能抓到"返回值被写成 {@code DROP} 之类常量"
 * （变异验过：改成 DROP → 两条红），但抓不到"返回值被写成 {@code PLAIN}" —— 因为经验卡
 * 那条路的判定<b>本来就是</b> PLAIN，两者不可区分。真正会跟 PLAIN 不同的只有物品路，
 * 而物品路离线跑不了。所以物品路的返回值 + {@code PickupRelay} 的 arm 接线，
 * 最后的保障是启动期的 {@code require=2}/{@code allow=2} 与一次真机的"听得到/听不到"，
 * 不是这几条单测。改这里时请照旧保持这个边界。
 */
class InboxOfferTest {

    @Test
    void experienceOfferReturnsAPlainShownDecision() {
        Inbox.INSTANCE.setSources(PickupCardSettings::defaults, FilterSettings::defaults);
        Inbox.INSTANCE.reset();

        FilterRules.Decision decision = Inbox.INSTANCE.offer(new CardContent.Experience(), 1);

        // 经验卡不过滤：照常弹、不强调、绝不静音（它的拾取音必须留）
        assertTrue(decision.show(), "经验卡要弹卡");
        assertFalse(decision.emphasized(), "经验卡不强调");
        assertFalse(decision.muted(), "经验卡恒不静音 —— 它没有过滤表，判定不该是常量 DROP");
    }

    @Test
    void experienceOfferIsNeverMutedEvenWhenTheMuteListIsNotEmpty() {
        // 反例：静音名单里塞了东西，经验卡也不该被牵连（经验不过滤是刻意的）
        Inbox.INSTANCE.setSources(PickupCardSettings::defaults,
                () -> new FilterSettings(List.of(), List.of(), List.of("minecraft:stone")));
        Inbox.INSTANCE.reset();

        FilterRules.Decision decision = Inbox.INSTANCE.offer(new CardContent.Experience(), 1);

        assertFalse(decision.muted(), "静音名单只作用于物品，经验卡的音要留");
        assertTrue(decision.show());
    }

    @Test
    void resetClearsTheSoundGate() {
        // 换世界/总开关关掉时，在途的"压音"要一起忘掉；否则它会跨世界泄漏到下一次拾取。
        PickupSoundGate.arm(true);
        Inbox.INSTANCE.reset();
        assertFalse(PickupSoundGate.consumeMute(), "reset 之后闸门必须是干净的");
    }
}
