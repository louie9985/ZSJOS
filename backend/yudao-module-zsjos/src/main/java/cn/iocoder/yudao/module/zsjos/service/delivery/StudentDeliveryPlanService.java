package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO;
import java.time.LocalDateTime;

public interface StudentDeliveryPlanService {
    StudentDeliveryPlanDO ensurePlan(Long studentPersonId, Long accountId, Long serviceRelationId,
                                     Long directorUserId, LocalDateTime accountOpenedAt);
    void createNextStages(Long planId, Long accountId, Long directorUserId, String completedStage,
                          LocalDateTime completedAt);
}
