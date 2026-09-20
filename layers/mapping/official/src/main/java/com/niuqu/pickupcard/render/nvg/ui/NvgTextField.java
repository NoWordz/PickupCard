package com.niuqu.pickupcard.render.nvg.ui;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 文本框：<b>点一下拿焦点，光标闪，退格能删，回车提交并放焦点，Esc 放弃。</b>
 *
 * <p>【它服务的两种活儿，差别只在"什么时候算数"】颜色框是<b>每敲一个字就生效</b> ——
 * 颜色没有"提交"这个动作，打了合法值就该立刻在预览里看见。过滤名单是<b>回车才生效</b> ——
 * 敲到一半的 {@code minecraft:cobb} 不该被当成一条规则记进配置。所以"字符集 / 长度上限 /
 * 敲字时通不通知 / 回车做什么"四件事由构造参数给，类还是这一个。
 *
 * <p>【为什么不再写"只有颜色需要键盘"】过滤名单也要打 {@code minecraft:cobblestone}、
 * {@code #forge:ores}、{@code @modid}。旧注释把"当时只有它要打字"当成了长期理由，
 * 于是一旦有第二种需要打字的控件，那句话就变成了错的。
 *
 * <p>【焦点为什么没有扩散】焦点仍然只在这一个控件类型里：没有 Tab 漫游、没有快捷键、
 * 没有拖选。多一种要打字的控件不等于要长成第二个 UI 框架。
 */
public final class NvgTextField extends NvgWidget {

    private final Supplier<String> value;
    /** 敲字时通知谁；{@code null} = 只改本地编辑值，等回车才交出去。 */
    private final @Nullable Consumer<String> onChange;
    private final Predicate<Character> accepts;
    private final int maxLength;
    /** 回车做什么；{@code null} = 回车只是放焦点（颜色框就是这样）。 */
    private final @Nullable Consumer<String> onSubmit;
    /** 没内容又没在编辑时显示的灰字提示（空串 = 什么都不显示）。 */
    private String placeholder = "";

    private @Nullable String draft;
    private boolean editing;

    public NvgTextField(String label, Supplier<String> value, @Nullable Consumer<String> onChange,
                        Predicate<Character> accepts, int maxLength,
                        @Nullable Consumer<String> onSubmit) {
        super(label);
        this.value = value;
        this.onChange = onChange;
        this.accepts = accepts;
        this.maxLength = maxLength;
        this.onSubmit = onSubmit;
    }

    /** 颜色框：只收 {@code #AARRGGBB} 的字符，最多 9 个，每敲一个字就生效。 */
    public static NvgTextField color(String label, Supplier<String> value,
                                     Consumer<String> onChange) {
        return new NvgTextField(label, value, onChange,
                c -> c == '#' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                        || (c >= 'A' && c <= 'F'),
                9, null);
    }

    /** 规则框：收任意可打印字符，最多 {@code maxLength} 个，<b>回车才提交</b>。 */
    public static NvgTextField rule(String label, Supplier<String> value, int maxLength,
                                    Consumer<String> onSubmit) {
        return new NvgTextField(label, value, null,
                c -> !Character.isISOControl(c), maxLength, onSubmit);
    }

    /** 空着的时候显示一句灰字（"在这里写一条"这种）。 */
    public NvgTextField placeholder(String text) {
        this.placeholder = text == null ? "" : text;
        return this;
    }

    @Override
    public String value() {
        return value.get();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hit = super.mouseClicked(mouseX, mouseY, button);
        if (hit) {
            draft = value.get();
            editing = true;
        }
        return hit;
    }

    @Override
    public void blur() {
        super.blur();
        editing = false;
        draft = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!editing) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (draft != null && !draft.isEmpty()) {
                draft = draft.substring(0, draft.length() - 1);
                notifyChange();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String submitted = draft == null ? "" : draft;
            editing = false;
            draft = null;
            if (onSubmit != null) {
                onSubmit.accept(submitted);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Esc = 放弃这一稿。颜色框那种"每敲一个字就生效"的，放弃也回不去，
            // 所以它靠 blur() 收尾；这里只负责别再往下传（否则会把界面关掉）。
            editing = false;
            draft = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        if (!editing) {
            return false;
        }
        if (draft != null && draft.length() >= maxLength) {
            return true;      // 吃满就不再收，但别让这个键漏到别处去
        }
        if (!accepts.test(c)) {
            return true;
        }
        draft = (draft == null ? "" : draft) + c;
        notifyChange();
        return true;
    }

    private void notifyChange() {
        if (onChange != null && draft != null) {
            onChange.accept(draft);
        }
    }

    /** 是否正在编辑；编辑期间显示的是本地编辑值，没编辑时显示的是真实值。 */
    public boolean editing() {
        return editing;
    }

    @Override
    protected void paint(NvgUi ui) {
        NvgPalette p = ui.palette;
        ui.well(x, y, w, h, wellColor(p));
        String shown = editing ? (draft == null ? "" : draft) : value.get();
        // 【宽度约束必须有】英文占位文案比框宽（"Type a rule and press Enter" 在右对齐的
        // 45% 等分行里装不下）：无约束的 ui.text 把字画到框外，越过的部分被滚动视口裁掉，
        // 框右缘只留下被切的半个字母——2026-09-20 截图里那粒“神秘的竖点”就是 and 的 a。
        // 装不下整体缩小，与 NvgButton 的值同一策略。
        if (shown.isEmpty() && !placeholder.isEmpty() && !editing) {
            ui.textFitted(placeholder, x + 4f, y + (h - ui.font().lineHeight) / 2f, p.textDim, w - 8f);
        } else {
            ui.textFitted(shown, x + 4f, y + (h - ui.font().lineHeight) / 2f, p.text, w - 8f);
        }
        if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
            // 光标：闪，且跟在文字后面 —— 不闪的话玩家分不清"在编辑"还是"只是显示"
            float caret = x + 5f + ui.textWidth(shown);
            ui.fillRoundRect(caret, y + 3f, 1f, h - 6f, 0.5f, p.text);
        }
    }
}
