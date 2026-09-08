package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductSpecSnapshotTest {
    @Test void namedLabelsRoundTripAndUnchangedSelectionKeepsHistoricalLabels() {
        var oldSpecs = List.of(new ProductSpecVO("level", "原等级", "2", "原二级", false));
        var old = new LeadIntendedProductDO(); old.setSpuRef("computer"); old.setSpuNameSnapshot("原产品"); old.setSkuRef("sku1");
        old.setSpuUnknown(false); old.setSkuUnknown(false); old.setSelectedSpecsJson(JsonUtils.toJsonString(oldSpecs));
        old.setPriceSnapshot(new java.math.BigDecimal("100"));
        var current = LeadProductSnapshot.of("computer", "新产品", List.of(new ZsjosProductCategoryPathNodeVO(1L, "考试")))
                .withSku("sku1", "新班级", "{\"level\":\"2\"}", java.math.BigDecimal.ZERO);
        var retained = current.retainSelection(List.of(old));
        assertEquals("原产品", retained.name());
        assertEquals(java.math.BigDecimal.ZERO, retained.price());
        assertEquals("原等级：原二级", retained.displaySpecs().getFirst().displayText());
        assertEquals(retained, JsonUtils.parseObject(JsonUtils.toJsonString(retained), LeadProductSnapshot.class));
        assertSame(current, current.retainSelection(List.of()));
    }
    @Test void legacyFieldsKeepDistinctNamesEvenWhenValuesEqual() {
        var legacy = LeadProductSnapshot.unknown().withSku("sku1", "旧规格", "{\"one\":\"same\",\"two\":\"same\"}", null);
        assertEquals(2, legacy.displaySpecs().size());
        assertTrue(legacy.displaySpecs().stream().allMatch(ProductSpecVO::labelMissing));
    }
    @Test void malformedLegacyAttributesNeverInventCurrentLabels() {
        var legacy = LeadProductSnapshot.unknown().withSku("sku1", "旧规格", "broken", null);
        assertEquals(List.of(), legacy.displaySpecs());
        assertEquals(List.of(), legacy.withSku("sku1", "旧规格", "null", null).displaySpecs());
    }
}
