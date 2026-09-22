package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.filter.RuleListEdit;
import dev.e33.trellis.ui.widget.Button;
import dev.e33.trellis.ui.widget.TextField;
import dev.e33.trellis.ui.widget.Widget;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 「过滤」页的三张名单：一条规则一行、末尾一行输入框。
 *
 * <p>【为什么从界面里搬出来（2026-09-19 架构审计）】三张名单不是"一个值"，是<b>可增删的
 * 列表</b> —— 行数随玩家加多少条涨，注定进不了 {@link ConfigPageSpec} 那张静态注册表。
 * 把它单独成一个类，界面类里就不再有"列表编辑"这摊事；判定规则本身仍然只有一处
 * （{@code shared} 的 {@code FilterRules}），这里只管摆行与回写。
 *
 * <p>【为什么拒绝必须说出来】"打字 → 回车 → 什么都没发生"是这类输入框最常见的失败方式，
 * 而底部那行是界面唯一能自我解释的地方 —— 拒绝原因经 {@link Host#rejectNote} 交回去。
 */
public final class FilterPageBuilder {

    /** 「恢复本页默认」清掉几张名单（恢复说明那句要用）。 */
    public static final int RESTORE_COUNT = 3;

    /** 界面要替本类做的三件事：摆一行、说一句话、请求重建。 */
    public interface Host {

        void cell(String label, Widget widget, String hint);

        /** 加规则被拒时说的一句话，短时间内在底部那行顶掉悬停说明。 */
        void rejectNote(String message);

        /** 名单变了 = 行数变了，必须重建（preserveScroll = 同页重建保留滚动位置）。 */
        void requestRebuild(boolean preserveScroll);
    }

    private FilterPageBuilder() {
    }

    /** 摆出三张名单。每张 = 一行表头（右侧只读钮报条数）+ 每条规则一行 + 一行输入框。 */
    public static void build(PickupCardConfig.Values v, Host host) {
        filterList(I18n.get("pickupcard.config.filter.blacklist"), v.blacklist,
                I18n.get("pickupcard.config.filter.blacklist.hint"),
                I18n.get("pickupcard.config.filter.blacklist.input"),
                host);
        filterList(I18n.get("pickupcard.config.filter.whitelist"), v.whitelist,
                I18n.get("pickupcard.config.filter.whitelist.hint"),
                I18n.get("pickupcard.config.filter.whitelist.input"),
                host);
        filterList(I18n.get("pickupcard.config.filter.mute"), v.muteList,
                I18n.get("pickupcard.config.filter.mute.hint"),
                I18n.get("pickupcard.config.filter.mute.input"),
                host);
    }

    /** 「恢复本页默认」：清空三张名单。 */
    public static void restoreDefaults(PickupCardConfig.Values v) {
        v.blacklist.set(List.of());
        v.whitelist.set(List.of());
        v.muteList.set(List.of());
        ConfigPageSpec.changed();
    }

    private static void filterList(String title, ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                                   String what, String inputHint, Host host) {
        List<String> rules = rules(config);
        // 表头这一行：标签是名单名，右边那颗只读钮报"现在几条"——只读控件的底更暗、不画描边，
        // 一眼能看出它点不动（见 Button 的 action == null）
        // 【单复数分键】英文 "1 rule" / "2 rules" 是两个词形；MC 1.20.1 的语言系统没有复数
        // 支持，一个 "%s rules" 键在一张名单只剩一条时就是语法错误（用户截图里的 "1 rules"）。
        // 中文两个键同形，key 集仍两端一致（LangKeyConsistencyTest 钉着）。
        host.cell(title, new Button("", () -> I18n.get(rules.size() == 1
                ? "pickupcard.config.filter.count.one" : "pickupcard.config.filter.count",
                rules.size()), null), what);
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            int index = i;
            host.cell(rule, new Button("", () -> I18n.get("pickupcard.config.filter.delete"), () -> writeRules(config,
                    RuleListEdit.remove(rules(config), index), host)),
                    // 【为什么把规则原文放在最前】标签那一格只有几十像素宽，长规则在屏上就是
                    // "minecraft:cobb" —— 底部这行是唯一能看全的地方
                    I18n.get("pickupcard.config.filter.removeHint", rule, title));
        }
        host.cell(I18n.get("pickupcard.config.filter.addRow"), TextField
                .rule("", () -> "", RuleListEdit.MAX_RULE_LENGTH, text -> addRule(config, title, text, host))
                .placeholder(I18n.get("pickupcard.config.filter.placeholder")), inputHint);
    }

    private static List<String> rules(ForgeConfigSpec.ConfigValue<List<? extends String>> config) {
        List<? extends String> raw = config.get();
        return raw == null ? List.of() : List.copyOf(raw);
    }

    private static void writeRules(ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                                   List<String> rules, Host host) {
        config.set(List.copyOf(rules));
        ConfigPageSpec.changed();
        // 【必须重建】名单变了 = 行数变了：不重建的话删掉的那一行会留在屏上继续可点，
        // 而它背后的下标已经指向别人了。同页重建保留滚动位置。
        host.requestRebuild(true);
    }

    private static void addRule(ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                                String title, String text, Host host) {
        RuleListEdit.Result r = RuleListEdit.add(rules(config), text);
        if (r.ok()) {
            writeRules(config, r.rules(), host);
            return;
        }
        host.rejectNote(I18n.get("pickupcard.config.filter.rejected", title, RuleListEdit.message(r.reject())));
    }
}
