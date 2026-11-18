package com.niuqu.pickupcard.render.nvg.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置界面那三列的几何。
 * <p>
 * 【为什么按宽度扫】列重叠、预览挤成一条、标签盖住配置列 —— 这些坏法都是"某一档画布下"
 * 才出现的。只测一个宽度等于没测，而缩放档就是这么漂的（guiScale 3 是 427 宽、5 只剩 256）。
 */
class ConfigLayoutTest {

    private static final float[] WIDTHS = {180f, 240f, 256f, 320f, 427f, 480f, 640f, 854f, 1280f};
    private static final float[] HEIGHTS = {144f, 180f, 240f, 360f, 480f};

    @Test
    @DisplayName("任何画布：三块矩形都在屏幕内、互不重叠、配置列永远在")
    void neverOverflowsAnyCanvas() {
        for (float w : WIDTHS) {
            for (float h : HEIGHTS) {
                ConfigLayout lo = ConfigLayout.compute(w, h);
                String at = (int) w + "x" + (int) h;
                ConfigLayout.Rect items = lo.items();
                ConfigLayout.Rect tabs = lo.tabs();

                assertTrue(items.right() <= w - ConfigLayout.MARGIN + 0.01f,
                        at + " 配置列溢出屏幕右边：" + items.right());
                assertTrue(items.h() > 0f && tabs.h() > 0f, at + " 有零高的列");

                if (lo.tabsOnTop()) {
                    assertTrue(tabs.bottom() <= items.y() - ConfigLayout.GAP + 0.01f,
                            at + " 顶排标签压住配置列");
                    assertEquals(ConfigLayout.MARGIN, tabs.x(), 0.01f, at + " 顶排标签贴边距");
                    assertEquals(ConfigLayout.MARGIN, items.x(), 0.01f, at + " 顶排时配置列贴边距");
                    assertTrue(tabs.right() <= w - ConfigLayout.MARGIN + 0.01f, at + " 顶排标签溢出");
                } else {
                    assertEquals(ConfigLayout.MARGIN, tabs.x(), 0.01f, at + " 标签列贴边距");
                    assertEquals(tabs.right() + ConfigLayout.GAP, items.x(), 0.01f,
                            at + " 列排时配置列要紧跟标签列");
                }

                if (lo.previewVisible()) {
                    assertTrue(lo.preview().x() >= items.right() + ConfigLayout.GAP - 0.01f,
                            at + " 预览与配置列重叠");
                    assertTrue(lo.preview().right() <= w - ConfigLayout.MARGIN + 0.01f,
                            at + " 预览溢出屏幕");
                    assertTrue(lo.preview().w() >= ConfigLayout.PREVIEW_MIN - 0.01f,
                            at + " 预览比下限还窄却还画");
                    assertEquals(items.h(), lo.preview().h(), 0.01f, at + " 预览与配置列不等高");
                }
            }
        }
    }

    @Test
    @DisplayName("按宽度退让：427 三列全在 → 320 收预览 → 240 标签变顶排")
    void retreatsInOrder() {
        ConfigLayout wide = ConfigLayout.compute(427f, 240f);
        assertTrue(wide.previewVisible(), "427（dev 画布）要摆得下三列，不然截图看不到真实排布");
        assertFalse(wide.tabsOnTop());

        ConfigLayout mid = ConfigLayout.compute(320f, 180f);
        assertFalse(mid.previewVisible(), "320 宽先收预览");
        assertFalse(mid.tabsOnTop(), "标签列还在");
        assertTrue(mid.items().w() > wide.items().w(), "预览让出来的宽度归配置列");

        ConfigLayout narrow = ConfigLayout.compute(240f, 180f);
        assertTrue(narrow.tabsOnTop(), "240 宽标签挪到顶上一行");
        assertTrue(narrow.items().w() >= ConfigLayout.ITEMS_MIN - 0.01f, "配置列永远在");
    }

    @Test
    @DisplayName("标签格子：列排从上往下、顶排从左往右，四颗都摆得下")
    void tabCells() {
        ConfigLayout col = ConfigLayout.compute(427f, 240f);
        float lastBottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            ConfigLayout.Rect r = col.tabRect(i, 4);
            assertTrue(r.y() >= lastBottom - 0.01f, "第 " + i + " 颗与上一颗重叠");
            lastBottom = r.bottom();
            assertTrue(r.right() <= col.tabs().right() + 0.01f, "第 " + i + " 颗横向越界");
        }

        ConfigLayout top = ConfigLayout.compute(240f, 180f);
        float lastRight = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            ConfigLayout.Rect r = top.tabRect(i, 4);
            assertTrue(r.x() >= lastRight - 0.01f, "顶排第 " + i + " 颗与上一颗重叠");
            lastRight = r.right();
            assertTrue(lastRight <= top.tabs().right() + 0.01f,
                    "顶排第 " + i + " 颗摆到了标签条外面");
        }
    }

    @Test
    @DisplayName("预览内部：卡区与切样例行都在面板里，四颗按钮等宽不重叠")
    void previewInternals() {
        for (float w : WIDTHS) {
            for (float h : HEIGHTS) {
                ConfigLayout lo = ConfigLayout.compute(w, h);
                if (!lo.previewVisible()) continue;
                String at = (int) w + "x" + (int) h;
                ConfigLayout.Rect card = lo.previewCard();
                assertTrue(card.x() >= lo.preview().x() - 0.01f, at + " 卡区跑到面板左边外面");
                assertTrue(card.right() <= lo.preview().right() + 0.01f, at + " 卡区跑到面板右边外面");
                assertTrue(card.bottom() <= lo.preview().bottom() + 0.01f, at + " 卡区跑到面板下面");
                if (!lo.switcherVisible()) continue;

                assertTrue(card.h() >= ConfigLayout.PREVIEW_CARD_MIN - 0.01f,
                        at + " 切样例行占了卡的地方：" + card.h());
                float lastRight = Float.NEGATIVE_INFINITY;
                ConfigLayout.Rect first = lo.switchRect(0, 4);
                for (int i = 0; i < 4; i++) {
                    ConfigLayout.Rect r = lo.switchRect(i, 4);
                    assertEquals(first.w(), r.w(), 0.01f, at + " 样例按钮不等宽");
                    assertTrue(r.x() >= lastRight - 0.01f, at + " 第 " + i + " 颗样例按钮重叠");
                    lastRight = r.right();
                }
                assertTrue(lastRight <= lo.preview().right() + 0.01f, at + " 样例按钮溢出面板");
                assertTrue(lo.switchRect(0, 4).y() >= card.bottom() - 0.01f,
                        at + " 样例按钮压住了卡");
            }
        }
    }

    @Test
    @DisplayName("预览收起时：切换行与卡区都是零矩形（点了不能命中）")
    void noSwitcherWhenPreviewCollapsed() {
        ConfigLayout lo = ConfigLayout.compute(320f, 180f);
        assertFalse(lo.previewVisible(), "320 宽这一档预览本来就该收起");
        assertFalse(lo.switcherVisible(), "预览收起了就不能画切样例行");
        assertEquals(0f, lo.switchRect(0, 4).w(), 0.01f, "收起时按钮宽度为 0");
        assertEquals(0f, lo.previewCard().w(), 0.01f, "收起时卡区宽度为 0");
    }
}
