package cn.iocoder.yudao.module.zsjos.service.lead.product;

import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class ZsjosLeadProductCatalogAdapter implements LeadProductCatalogPort {
    @Resource
    private ZsjosProductService productService;

    @Override
    public List<LeadProductSnapshot> getEnabledProducts() {
        return productService.getEnabledSimpleList().stream()
                .map(item -> LeadProductSnapshot.of(item.productRef(), item.name(), item.categoryPath())).toList();
    }

    @Override
    public List<LeadProductSnapshot> getEnabledProducts(Collection<String> productRefs) {
        return productService.validateEnabledProducts(productRefs);
    }
}
