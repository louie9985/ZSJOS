package cn.iocoder.yudao.module.zsjos.framework.allinpay;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllinpaySignerTest {

    @Test
    void canonicalShouldSortAndExcludeUnsignedValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("trxamt", "100");
        values.put("sign", "ignored");
        values.put("appid", "app");
        values.put("empty", "");
        values.put("nullable", null);

        assertEquals("appid=app&trxamt=100", AllinpaySigner.canonical(values));
    }
}
