package cn.iocoder.yudao.framework.common.util.number;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyUtilsTest {

    @Test
    void testPriceScaleAndAdd() {
        assertEquals(new BigDecimal("0.00"), MoneyUtils.priceScale(null));
        assertEquals(new BigDecimal("1.24"), MoneyUtils.priceScale(new BigDecimal("1.235")));
        assertEquals(new BigDecimal("3.34"), MoneyUtils.priceAdd(
                new BigDecimal("1.111"), null, new BigDecimal("2.229")));
        assertEquals(new BigDecimal("0.00"), MoneyUtils.priceAdd((BigDecimal[]) null));
    }

}
