package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedFilterProductOptionsTest {
    @Mock ZsjosProductSkuService products;
    @InjectMocks AdvancedFilterProductOptions resolver;

    @Test void resolvesStableReferencesAndDisambiguatesSkuNamesWithoutPrices() {
        when(products.getLeadCatalog()).thenReturn(new LeadProductCatalogRespVO(List.of(), List.of(
                new LeadProductCatalogRespVO.Spu(null, null, List.of(), null, null, null, null, "p-1", "课程甲", List.of())),
                List.of(new LeadProductCatalogRespVO.Sku("p-1", "sku-1", "基础班", Map.of(), new java.math.BigDecimal("123.45")))));
        var result = resolver.resolve(new AdvancedFilterService().catalog("lead"));
        var sku = result.fields().stream().filter(f -> f.fieldKey().equals("lead.intendedSkuRef")).findFirst().orElseThrow();
        assertNull(sku.optionSource());
        assertEquals("product-catalog:sku", sku.declaredOptionSource());
        assertEquals("business_api", sku.optionSourceType());
        assertEquals("sku-1", sku.options().getFirst().value());
        assertEquals("课程甲 / 基础班", sku.options().getFirst().label());
        assertFalse(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result).contains("123.45"));
        verify(products, times(1)).getLeadCatalog();
    }

    @Test void emptyCatalogStaysEmptyWithoutClientFallback() {
        when(products.getLeadCatalog()).thenReturn(new LeadProductCatalogRespVO(List.of(), List.of(), List.of()));
        var result = resolver.resolve(new AdvancedFilterService().catalog("order"));
        var field = result.fields().stream().filter(f -> f.fieldKey().equals("orderItem.skuRef")).findFirst().orElseThrow();
        assertEquals("empty", field.optionsState());
        assertNull(field.optionSource());
        assertTrue(field.options().isEmpty());
    }

    @Test void unrelatedScenesDoNotReadProductsAndFailuresDoNotInventOptions() {
        resolver.resolve(new AdvancedFilterService().catalog("withdrawal"));
        verifyNoInteractions(products);
        when(products.getLeadCatalog()).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> resolver.resolve(new AdvancedFilterService().catalog("order")));
    }
}
