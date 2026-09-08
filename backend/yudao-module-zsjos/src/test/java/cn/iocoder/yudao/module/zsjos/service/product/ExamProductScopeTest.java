package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamProductScopeTest {
    @InjectMocks ZsjosProductSkuServiceImpl service;
    @Mock ZsjosProductService productService;
    @Mock ZsjosProductMapper productMapper;
    @Mock ZsjosProductAttrMapper attrMapper;
    @Mock ZsjosProductAttrValueMapper attrValueMapper;
    @Mock ZsjosProductSkuMapper skuMapper;
    @Mock ProductCategoryLocks categoryLocks;

    @BeforeEach void setup() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        var product = new ZsjosProductDO(); product.setId(1L); product.setProductRef("computer"); product.setName("计算机考试"); product.setCategoryId(10L);
        product.setStatus(0);
        when(productMapper.selectByIdForUpdate(1L, 1L)).thenReturn(product);
        var category = new ZsjosProductCategoryDO().setId(10L).setParentId(0L).setStatus(0);
        when(categoryLocks.paths(List.of(10L))).thenReturn(Map.of(10L, category));
        when(productService.validateEnabledProducts(List.of("computer"))).thenReturn(List.of(
                LeadProductSnapshot.of("computer", "计算机考试", List.of(new ZsjosProductCategoryPathNodeVO(10L, "考试")))));
        var place = attr(1L, "place", "考试地点"); var level = attr(2L, "level", "考试等级");
        when(attrMapper.selectListBySpuId(1L)).thenReturn(List.of(place, level));
        when(attrValueMapper.selectListByAttrIds(List.of(1L, 2L))).thenReturn(List.of(
                value(1L, "bj", "北京"), value(1L, "sh", "上海"), value(2L, "2", "二级"), value(2L, "3", "三级")));
        lenient().when(skuMapper.selectEnabledListBySpuIds(List.of(1L))).thenReturn(List.of(sku(1L, "bj", "2"), sku(2L, "sh", "2")));
    }
    @org.junit.jupiter.api.AfterEach void cleanup() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }

    @Test void partialConditionsMatchAllAndOnlyCompatibleSkus() {
        var scope = service.resolveExamScope(1L, Map.of("level", "2"));
        assertEquals(2, scope.skus().size());
        assertEquals(List.of("考试等级：二级"), scope.selectedSpecs().stream().map(ProductSpecVO::displayText).toList());
        assertEquals(1, service.resolveExamScope(1L, Map.of("level", "2", "place", "bj")).skus().size());
        assertEquals(2, service.resolveExamScope(1L, Map.of()).skus().size());
    }
    @Test void rejectsInvalidKeysAndNonexistentCombination() {
        assertThrows(ServiceException.class, () -> service.resolveExamScope(1L, Map.of("skuId", "1")));
        var error = assertThrows(ServiceException.class, () -> service.resolveExamScope(1L, Map.of("level", "3")));
        assertEquals(1_900_018_008, error.getCode());
    }
    @Test void specOrderingUsesMetadataNotRequestMapOrder() {
        assertEquals(List.of("place", "level"), service.resolveExamScope(1L, Map.of("level", "2", "place", "bj"))
                .selectedSpecs().stream().map(ProductSpecVO::attrKey).toList());
    }
    private static ZsjosProductAttrDO attr(Long id, String key, String name) {
        var a = new ZsjosProductAttrDO(); a.setId(id); a.setAttrKey(key); a.setAttrName(name); a.setRequired(true); a.setStatus(0); return a;
    }
    private static ZsjosProductAttrValueDO value(Long id, String value, String label) {
        var v = new ZsjosProductAttrValueDO(); v.setAttrId(id); v.setValue(value); v.setLabel(label); v.setStatus(0); return v;
    }
    private static ZsjosProductSkuDO sku(Long id, String place, String level) {
        var s = new ZsjosProductSkuDO(); s.setId(id); s.setSkuRef("sku" + id); s.setSkuName("组合" + id);
        s.setAttrValuesJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(Map.of("place", place, "level", level))); return s;
    }
}
