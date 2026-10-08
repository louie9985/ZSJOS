package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalProductSnapshotTest {
    @Test void readsAllLegacyFlagCombinationsWithoutChangingRecordedFacts() {
        for (String spu : new String[] {"", ",\"spuUnknown\":null", ",\"spuUnknown\":false", ",\"spuUnknown\":true"}) {
            for (String sku : new String[] {"", ",\"skuUnknown\":null", ",\"skuUnknown\":false", ",\"skuUnknown\":true"}) {
                var result = LeadProductSnapshot.readHistorical("{\"name\":\"旧课程\",\"skuName\":\"旧规格\",\"price\":12.50" + spu + sku + "}");
                assertEquals("旧课程", result.name()); assertEquals("旧规格", result.skuName());
                assertEquals(new java.math.BigDecimal("12.50"), result.price());
                assertEquals(spu.endsWith("true"), result.spuUnknown());
                assertEquals(sku.endsWith("true"), result.skuUnknown());
            }
        }
    }
    @Test void preservesHighPrecisionHistoricalAmount() {
        var snapshot = LeadProductSnapshot.readHistorical("{\"price\":123456789.12345678901234567890,\"spuUnknown\":null}");
        assertEquals(new java.math.BigDecimal("123456789.12345678901234567890"), snapshot.price());
    }
    @Test void rejectsNonBooleanFlagsAndMalformedDataWithoutEchoingPayload() {
        for (String json : new String[] {"[]", "null", "private payload", "{\"spuUnknown\":\"false\"}", "{\"skuUnknown\":1}"}) {
            var ex = assertThrows(IllegalArgumentException.class, () -> LeadProductSnapshot.readHistorical(json));
            assertEquals("Invalid historical product snapshot", ex.getMessage()); assertNull(ex.getCause());
            assertNull(LeadProductSnapshot.readHistoricalQuietly(json));
        }
        assertNull(LeadProductSnapshot.readHistorical(null));
        assertNull(LeadProductSnapshot.readHistorical(" "));
    }
}
