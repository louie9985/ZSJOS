package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductSkuSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductAttrDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductAttrValueDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductSkuDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_PRICE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_SKU_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_SKU_COMBINATION_LIMIT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_SKU_SPU_IMMUTABLE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_REFS_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZsjosProductSkuServiceImplTest {
    @InjectMocks private ZsjosProductSkuServiceImpl service;
    @Mock private ZsjosProductService productService;
    @Mock private ZsjosProductMapper productMapper;
    @Mock private ZsjosProductCategoryMapper categoryMapper;
    @Mock private ZsjosProductAttrMapper attrMapper;
    @Mock private ZsjosProductAttrValueMapper attrValueMapper;
    @Mock private ZsjosProductSkuMapper skuMapper;
    @Mock private LeadIntendedProductMapper intendedProductMapper;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(service, "maxGeneratedCombinations", 500);
        ZsjosProductDO product = new ZsjosProductDO();
        product.setId(1L); product.setCategoryId(100L); product.setProductRef("spu-1");
        product.setName("测试产品"); product.setStatus(CommonStatusEnum.ENABLE.getStatus());
        lenient().when(productMapper.selectByIdForUpdate(anyLong(), eq(1L))).thenReturn(product);
        var category = new cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO();
        category.setId(100L); category.setParentId(0L); category.setStatus(CommonStatusEnum.ENABLE.getStatus());
        lenient().when(categoryMapper.selectByIdForUpdate(100L, 1L)).thenReturn(category);
    }

    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void validatesUnknownSpuAndSkuWithoutCreatingCatalogRows() {
        LeadProductSnapshot snapshot = service.validateLeadProduct(null, true, null, true);
        assertTrue(snapshot.spuUnknown());
        assertTrue(snapshot.skuUnknown());
        assertNull(snapshot.productRef());
        assertNull(snapshot.price());
    }

    @Test
    void rejectsRealSkuWhenSpuIsUnknown() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.validateLeadProduct(null, true, "sku_x", false));
        assertEquals(PRODUCT_SKU_INVALID.getCode(), error.getCode());
    }

    @Test
    void rejectsNegativeSkuPrice() {
        ZsjosProductSkuSaveReqVO reqVO = new ZsjosProductSkuSaveReqVO();
        reqVO.setSpuId(1L); reqVO.setSkuName("测试SKU"); reqVO.setPrice(new BigDecimal("-0.01"));
        reqVO.setStatus(1); reqVO.setSort(0);
        ServiceException error = assertThrows(ServiceException.class, () -> service.createSku(reqVO));
        assertEquals(PRODUCT_PRICE_INVALID.getCode(), error.getCode());
    }

    @Test
    void createDefaultsToEnabledAndOrdinaryUpdateOmitsStatusColumn() {
        ZsjosProductSkuSaveReqVO create = skuRequest(1L, 99);
        when(attrMapper.selectListBySpuId(1L)).thenReturn(List.of());
        when(attrValueMapper.selectListByAttrIds(List.of())).thenReturn(List.of());
        doAnswer(invocation -> { invocation.getArgument(0, ZsjosProductSkuDO.class).setId(10L); return 1; })
                .when(skuMapper).insert(any(ZsjosProductSkuDO.class));

        assertEquals(10L, service.createSku(create));
        verify(productMapper).selectByIdForUpdate(1L, 1L);
        verify(categoryMapper).selectByIdForUpdate(100L, 1L);
        verify(skuMapper).insert(argThat((ZsjosProductSkuDO sku) ->
                CommonStatusEnum.ENABLE.getStatus().equals(sku.getStatus())));

        ZsjosProductSkuDO existing = new ZsjosProductSkuDO();
        existing.setId(10L); existing.setSpuId(1L); existing.setSkuRef("sku-1");
        existing.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(skuMapper.selectById(10L)).thenReturn(existing);
        ZsjosProductSkuSaveReqVO update = skuRequest(1L, CommonStatusEnum.ENABLE.getStatus()); update.setId(10L);

        service.updateSku(update);

        verify(skuMapper).updateById(argThat((ZsjosProductSkuDO sku) -> sku.getStatus() == null));
    }

    @Test
    void createRejectsDisabledSpuBeforeReadingAttributesOrInserting() {
        ZsjosProductDO disabledProduct = new ZsjosProductDO();
        disabledProduct.setId(1L); disabledProduct.setCategoryId(100L);
        disabledProduct.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(productMapper.selectByIdForUpdate(1L, 1L)).thenReturn(disabledProduct);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createSku(skuRequest(1L, CommonStatusEnum.ENABLE.getStatus())));

        assertEquals(PRODUCT_REFS_INVALID.getCode(), error.getCode());
        verifyNoInteractions(attrMapper, attrValueMapper, skuMapper);
    }

    @Test
    void generateRejectsDisabledCategoryBeforeReadingAttributesOrInserting() {
        var disabledCategory = new cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO();
        disabledCategory.setId(100L); disabledCategory.setParentId(0L);
        disabledCategory.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(categoryMapper.selectByIdForUpdate(100L, 1L)).thenReturn(disabledCategory);

        ServiceException error = assertThrows(ServiceException.class, () -> service.generateSkus(1L));

        assertEquals(PRODUCT_REFS_INVALID.getCode(), error.getCode());
        verifyNoInteractions(attrMapper, attrValueMapper, skuMapper);
    }

    @Test
    void updateRejectsMovingSkuToAnotherSpu() {
        ZsjosProductSkuDO existing = new ZsjosProductSkuDO(); existing.setId(10L); existing.setSpuId(1L);
        when(skuMapper.selectById(10L)).thenReturn(existing);
        ZsjosProductSkuSaveReqVO request = skuRequest(2L, 0); request.setId(10L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateSku(request));

        assertEquals(PRODUCT_SKU_SPU_IMMUTABLE.getCode(), error.getCode());
        verifyNoInteractions(productService);
    }

    @Test
    void generateRejectsCartesianProductAboveConfiguredLimitBeforeInsert() {
        ReflectionTestUtils.setField(service, "maxGeneratedCombinations", 2);
        ZsjosProductAttrDO attr = new ZsjosProductAttrDO(); attr.setId(5L); attr.setAttrKey("level");
        when(attrMapper.selectListBySpuId(1L)).thenReturn(List.of(attr));
        when(attrValueMapper.selectListByAttrIds(List.of(5L))).thenReturn(List.of(
                attrValue(5L, "a"), attrValue(5L, "b"), attrValue(5L, "c")));

        ServiceException error = assertThrows(ServiceException.class, () -> service.generateSkus(1L));

        assertEquals(PRODUCT_SKU_COMBINATION_LIMIT.getCode(), error.getCode());
        verify(categoryMapper).selectByIdForUpdate(100L, 1L);
        verify(skuMapper, never()).insert(any(ZsjosProductSkuDO.class));
    }

    @Test
    void generateRejectsNonPositiveConfiguredLimit() {
        ReflectionTestUtils.setField(service, "maxGeneratedCombinations", 0);

        ServiceException error = assertThrows(ServiceException.class, () -> service.generateSkus(1L));

        assertEquals(PRODUCT_SKU_COMBINATION_LIMIT.getCode(), error.getCode());
        verifyNoInteractions(productService, attrMapper, attrValueMapper, skuMapper);
    }

    private ZsjosProductSkuSaveReqVO skuRequest(Long spuId, Integer status) {
        ZsjosProductSkuSaveReqVO request = new ZsjosProductSkuSaveReqVO();
        request.setSpuId(spuId); request.setSkuName("测试SKU"); request.setAttrValues(Map.of());
        request.setPrice(BigDecimal.TEN); request.setStatus(status); request.setSort(0);
        return request;
    }

    private ZsjosProductAttrValueDO attrValue(Long attrId, String value) {
        ZsjosProductAttrValueDO row = new ZsjosProductAttrValueDO();
        row.setAttrId(attrId); row.setValue(value); row.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return row;
    }
}
