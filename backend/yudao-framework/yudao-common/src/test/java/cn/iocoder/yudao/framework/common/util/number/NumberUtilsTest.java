package cn.iocoder.yudao.framework.common.util.number;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberUtilsTest {

    @Test
    void testParseFirstBigDecimal() {
        assertEquals(new BigDecimal("100"), NumberUtils.ONE_HUNDRED);
        assertEquals(new BigDecimal("1234.56"), NumberUtils.parseFirstBigDecimal("基数 1,234.56 元"));
        assertEquals(new BigDecimal("-8.5"), NumberUtils.parseFirstBigDecimal("下限-8.5%"));
        assertEquals(BigDecimal.ZERO, NumberUtils.parseFirstBigDecimal(null));
        assertEquals(BigDecimal.ZERO, NumberUtils.parseFirstBigDecimal("无数字"));
        assertEquals(BigDecimal.ZERO, NumberUtils.zeroIfNull(null));
    }

}
