package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;

import java.util.Collection;
import java.util.List;

public interface ZsjosProductService {
    Long createProduct(ZsjosProductSaveReqVO reqVO);
    void updateProduct(ZsjosProductSaveReqVO reqVO);
    void deleteProduct(Long id);
    void updateStatus(ZsjosProductStatusReqVO reqVO);
    ZsjosProductRespVO getProduct(Long id);
    PageResult<ZsjosProductRespVO> getProductPage(ZsjosProductPageReqVO reqVO);
    List<ZsjosProductSimpleRespVO> getEnabledSimpleList();
    List<LeadProductSnapshot> validateEnabledProducts(Collection<String> refs);
}
