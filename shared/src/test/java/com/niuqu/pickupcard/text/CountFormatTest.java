package com.niuqu.pickupcard.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountFormatTest {

    @Test
    @DisplayName("PLUS 只加一个正号：上一版在这里出过 +×1")
    void plusHasExactlyOnePrefix() {
        assertEquals("+1", CountFormat.PLUS.gain(1));
        assertEquals("+64", CountFormat.PLUS.gain(64));
        // 出现第二个符号就说明又被拼了一次前缀
        assertEquals(1, CountFormat.PLUS.gain(64).chars().filter(c -> c == '+' || c == '×').count());
    }

    @Test
    @DisplayName("X_PREFIX 是 × 而不是 +×")
    void xPrefixIsSingleX() {
        assertEquals("×64", CountFormat.X_PREFIX.gain(64));
        assertEquals(1, CountFormat.X_PREFIX.gain(64).chars().filter(c -> c == '×').count());
    }

    @Test
    void plainIsBareNumber() {
        assertEquals("64", CountFormat.PLAIN.gain(64));
    }

    @Test
    @DisplayName("缩写：千位起才缩写，且一位小数封顶")
    void abbreviated() {
        assertEquals("+999", CountFormat.ABBREVIATED.gain(999));
        assertEquals("+1.0K", CountFormat.ABBREVIATED.gain(1_000));
        assertEquals("+1.2K", CountFormat.ABBREVIATED.gain(1_234));
        // 过 100 就不要小数了：999K 比 999.0K 好看，也不占宽度
        assertEquals("+999K", CountFormat.ABBREVIATED.gain(999_000));
        assertEquals("+1.0M", CountFormat.ABBREVIATED.gain(1_000_000));
        assertEquals("+1.0B", CountFormat.ABBREVIATED.gain(1_000_000_000));
    }

    @Test
    @DisplayName("总数写法不带进账符号：口径是「背包里有多少」，不是「这次进账多少」")
    void holdHasNoGainPrefix() {
        assertEquals("64", CountFormat.PLUS.hold(64));
        assertEquals("64", CountFormat.X_PREFIX.hold(64));
        assertEquals("64", CountFormat.PLAIN.hold(64));
        assertEquals("1.2K", CountFormat.ABBREVIATED.hold(1_200));
        // 缩写档与 gain 同一把尺：千位起缩、一位小数封顶
        assertEquals("1.0K", CountFormat.ABBREVIATED.hold(1_000));
        assertEquals("999", CountFormat.ABBREVIATED.hold(999));
    }

    @Test
    @DisplayName("所有格式都不含空白：卡片宽度是算出来的，混进空格会量错")
    void noWhitespace() {
        for (CountFormat format : CountFormat.values()) {
            String text = format.gain(1_234_567);
            assertEquals(text.strip(), text, format + " 产生了首尾空白");
            assertEquals(-1, text.indexOf(' '), format + " 内部有空格");
        }
    }
}
