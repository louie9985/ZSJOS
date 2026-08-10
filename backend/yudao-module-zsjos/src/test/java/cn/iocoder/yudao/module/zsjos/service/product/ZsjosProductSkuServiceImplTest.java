package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductSkuSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_PRICE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_SKU_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZsjosProductSkuServiceImplTest {
    @InjectMocks private ZsjosProductSkuServiceImpl service;
    @Mock private ZsjosProductService productService;
    @Mock private ZsjosProductMapper productMapper;
    @Mock private ZsjosProductAttrMapper attrMapper;
    @Mock private ZsjosProductAttrValueMapper attrValueMapper;
    @Mock private ZsjosProductSkuMapper skuMapper;
    @Mock private LeadIntendedProductMapper intendedProductMapper;

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
        when(productService.getProduct(1L)).thenReturn(new ZsjosProductRespVO());
        ServiceException error = assertThrows(ServiceException.class, () -> service.createSku(reqVO));
        assertEquals(PRODUCT_PRICE_INVALID.getCode(), error.getCode());
    }
}
