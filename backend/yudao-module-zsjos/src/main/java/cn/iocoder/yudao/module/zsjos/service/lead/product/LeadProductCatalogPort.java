package cn.iocoder.yudao.module.zsjos.service.lead.product;

import java.util.Collection;
import java.util.List;

public interface LeadProductCatalogPort {

    List<LeadProductSnapshot> getEnabledProducts();

    List<LeadProductSnapshot> getEnabledProducts(Collection<String> productRefs);

}
