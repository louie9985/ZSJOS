package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductSimpleRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductCatalogPort;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeadProductService {
    @Resource
    private LeadProductCatalogPort catalogPort;

    public List<LeadProductSimpleRespVO> getEnabledProducts() {
        return catalogPort.getEnabledProducts().stream()
                .map(item -> new LeadProductSimpleRespVO(item.productRef(), item.name(), item.categoryId(),
                        item.categoryName(), item.categoryPath(), item.level1CategoryId(),
                        item.level1CategoryName(), item.level2CategoryId(), item.level2CategoryName())).toList();
    }
}
