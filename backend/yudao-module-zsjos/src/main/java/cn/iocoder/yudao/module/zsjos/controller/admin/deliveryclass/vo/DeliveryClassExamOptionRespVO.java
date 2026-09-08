package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO;
import java.util.List;

public record DeliveryClassExamOptionRespVO(Long id, String scheduleType, String displayName,
        Long productId, Long categoryId, List<ExamProductScopeRespVO.Sku> frozenSkus) {}
