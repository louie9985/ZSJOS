package cn.iocoder.yudao.module.zsjos.service.lead.product;


import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PRODUCT_CATALOG_UNAVAILABLE;

/**
 * 产品 SDK 尚未交付时的显式失败适配器，禁止生产流程退回静态课程数据。
 */
public class UnavailableLeadProductCatalogAdapter implements LeadProductCatalogPort {
    @Override
    public List<LeadProductSnapshot> getEnabledProducts() {
        throw exception(LEAD_PRODUCT_CATALOG_UNAVAILABLE);
    }

    @Override
    public List<LeadProductSnapshot> getEnabledProducts(Collection<String> productRefs) {
        throw exception(LEAD_PRODUCT_CATALOG_UNAVAILABLE);
    }
}
