package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.ui.widget.PaintCtx;
import dev.e33.trellis.ui.widget.Widget;
import dev.e33.trellis.ui.widget.WidgetPalette;
import net.minecraft.world.item.ItemStack;

/**
 * 网格里的一格：<b>真卡面</b>（底色 + 一颗真 MC 物品 + 名字）。
 *
 * <p>【它是 {@link PlaceholderCell} 的接任者，而换掉的正是那条挡了两轮的缺口】A-19 那一轮
 * 刻意用占位格避开了"L4 画不了 MC 物品"，把这条记成遗留⑤（{@code docs/plan.md}）。
 * A-25 把框架侧的缝（{@code BoxPainter}）立起来之后，真卡面才可能进树 ——
 * 而它进树的方式是<b>框架给盒子、宿主落笔</b>：见 {@link McBoxPainter}。
 *
 * <p>【为什么图标盒由本类算，而不是让缝自己算】{@code BoxPainter.paint(box, payload)} 里那个
 * {@code box} 是"<b>画在哪</b>"，不是"格子在哪" —— 所以"图标占格子中间多大一块"是<b>本类的事</b>
 * （同一个缝也要能画别的东西：徽章、缩略图、方块）。这也是它比"框架定一个图标 API"强的地方：
 * 框架不需要知道"卡片上图标多大"这种业务。
 *
 * <p>【已知观感】图标大小用的是宿主卡片的 {@code StyleModel.iconSize}（8–64），放在
 * 300×180 的格子里会显得小 —— 本轮要证的是"真物品能在树里画出来"，不是排版好不好看。
 */
public final class CardFaceCell extends Widget {

    private final ItemStack stack;

    /** 图标边长（逻辑 px）—— 由屏幕从宿主的卡片样式里取一次，24 格共用。 */
    private final float iconSize;

    /**
     * @param label    控件的名字（harness 按名字找它，见 {@link Widget#label()}）
     * @param stack    这一格显示哪个物品
     * @param iconSize 图标边长
     */
    public CardFaceCell(String label, ItemStack stack, float iconSize) {
        super(label);
        this.stack = stack;
        this.iconSize = iconSize;
    }

    @Override
    public String value() {
        return stack.isEmpty() ? "" : stack.getHoverName().getString();
    }

    /**
     * 画一格。几何一律从 {@code ctx} 来（就是 {@code bounds()} 那个对象本身），控件不存 {@code x/y/w/h}。
     *
     * <p>顺序：底色 → 描边 → <b>物品图标</b>（交给宿主自绘的缝）→ 名字。
     * 图标那一步不在这里画，只是<b>登记</b>（见 {@link McBoxPainter}）—— 所以它最后落到屏上的
     * 时机比这里晚，但位置是这里算的。
     */
    @Override
    protected void paint(PaintCtx ctx) {
        WidgetPalette p = ctx.palette();
        float w = ctx.width();
        float h = ctx.height();
        ctx.fillRoundRect(0f, 0f, w, h, p.radius(), wellColor(p));
        ctx.strokeRoundRect(0f, 0f, w, h, p.radius(), p.outline());

        // 图标盒：自己在格子里居中的一个正方形，往上挪半行给下面的名字让位。
        // 【必须是绝对坐标】缝的契约就是绝对逻辑坐标（延迟到帧尾时已经没有"当前递归"那回事了）。
        float side = Math.min(iconSize, Math.min(w, h));
        float ix = (w - side) / 2f;
        float iy = (h - side) / 2f - ctx.lineHeight() / 2f;
        // `6f` 是下面那行文字的左右留白：与 PlaceholderCell 同款既有先例（裸数字，本仓记过）。
        ctx.boxes().paint(new Rect(ctx.box().x() + ix, ctx.box().y() + iy, side, side), stack);

        // 名字：比格宽时整体缩小，不截断 —— 与 Button / PlaceholderCell 同一条口径。
        ctx.textCenteredFitted(value(), w / 2f, h - ctx.lineHeight() * 2f, p.text(), w - 6f);
    }
}
