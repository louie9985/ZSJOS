package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductSkuMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_HAS_SKUS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_IN_USE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZsjosProductServiceImplTest {
    @InjectMocks private ZsjosProductServiceImpl service;
    @Mock private ZsjosProductMapper productMapper;
    @Mock private LeadIntendedProductMapper intendedProductMapper;
    @Mock private ZsjosProductCategoryMapper categoryMapper;
    @Mock private ZsjosProductSkuMapper skuMapper;

    @Test
    void deleteRejectsProductReferencedByLeadWithActionableError() {
        ZsjosProductDO product = product(1L, "course-1");
        when(productMapper.selectById(1L)).thenReturn(product);
        when(intendedProductMapper.selectCountByProductRef("course-1")).thenReturn(1L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.deleteProduct(1L));

        assertEquals(PRODUCT_IN_USE.getCode(), error.getCode());
        verify(skuMapper, never()).selectCountBySpuId(anyLong());
        verify(productMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsProductWithSkusWithSkuSpecificError() {
        ZsjosProductDO product = product(1L, "course-1");
        when(productMapper.selectById(1L)).thenReturn(product);
        when(intendedProductMapper.selectCountByProductRef("course-1")).thenReturn(0L);
        when(skuMapper.selectCountBySpuId(1L)).thenReturn(1L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.deleteProduct(1L));

        assertEquals(PRODUCT_HAS_SKUS.getCode(), error.getCode());
        verify(productMapper, never()).deleteById(anyLong());
    }

    private static ZsjosProductDO product(Long id, String productRef) {
        ZsjosProductDO product = new ZsjosProductDO();
        product.setId(id);
        product.setProductRef(productRef);
        return product;
    }
}
