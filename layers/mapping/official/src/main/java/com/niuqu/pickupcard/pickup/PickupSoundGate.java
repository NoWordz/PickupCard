package com.niuqu.pickupcard.pickup;

/**
 * 一次拾取里，"这条音要不要压掉"从账本传到原版放音点的接力棒。
 *
 * <p>【为什么需要这么一只盒子】判定（静音名单命中）算在 {@link Inbox#offer}，而声音是
 * {@code ClientPacketListener.handleTakeItemEntity} 自己放的 —— 两者在同一次方法调用里，
 * 但相隔几行字节码，没有参数能把结论递过去。原版那个方法不能整段取消（取消了连物品
 * 扣减与实体移除都没了），所以只能从"放音"那一次调用下手：一个 {@code @Redirect}
 * 把两次 {@code playLocalSound} 引到这里问一句。
 *
 * <p>【时序是这条链唯一的硬约束】{@link PickupRelay} 注入在 {@code ensureRunningOnSameThread}
 * 之后（放音之前）：它先 {@link #arm}，原版接着读 {@link #consumeMute}。读必须发生在写之后、
 * 且在同一次调用的同一条线程上 —— 两者都跑在主线程，所以这里不需要任何同步。顺序一旦被
 * 破坏（比如把注入点挪到放音之后），症状是"静音名单时灵时不灵"，而不是报错；
 * 挪注入点时记得一起看这里。
 *
 * <p>【读一次就复位】{@link #consumeMute} 读走并把状态复位。这样即使某次拾取没有走到放音
 * 那一步（实体已被移除之类），残留的"压"也不会漏到下一次拾取上。{@link Inbox#reset()} 也会
 * 顺手清零 —— 换世界/总开关关掉时，"这一次拾取"本来就该忘掉。
 */
public final class PickupSoundGate {

    private static boolean mute;

    private PickupSoundGate() {
    }

    /** 记下"这一次拾取该不该压音"。{@link PickupRelay} 每收到一条拾取包都会调一次（两个分支都调）。 */
    public static void arm(boolean shouldMute) {
        mute = shouldMute;
    }

    /** 放音前问一次：true = 压掉这条音。读过即复位成"不压"。 */
    public static boolean consumeMute() {
        boolean armed = mute;
        mute = false;
        return armed;
    }
}
