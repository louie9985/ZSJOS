package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;

import java.util.List;

public interface ZsjosProductSkuService {
    List<ZsjosProductAttrRespVO> getAttrs(Long spuId);
    void saveAttrs(ZsjosProductAttrSaveReqVO reqVO);
    Long createSku(ZsjosProductSkuSaveReqVO reqVO);
    int generateSkus(Long spuId);
    void updateSku(ZsjosProductSkuSaveReqVO reqVO);
    void deleteSku(Long id);
    void updateSkuStatus(ZsjosProductSkuStatusReqVO reqVO);
    ZsjosProductSkuRespVO getSku(Long id);
    List<ZsjosProductSkuRespVO> getSkuList(Long spuId);
    LeadProductCatalogRespVO getLeadCatalog();
    LeadProductSnapshot validateLeadProduct(String spuRef, boolean spuUnknown, String skuRef, boolean skuUnknown);
}
