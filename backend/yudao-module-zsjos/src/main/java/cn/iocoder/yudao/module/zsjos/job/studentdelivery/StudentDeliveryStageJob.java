package cn.iocoder.yudao.module.zsjos.job.studentdelivery;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component @Slf4j
public class StudentDeliveryStageJob implements JobHandler {
    @Resource private StudentDeliveryStageMapper stageMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryCycleService cycles;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper plans;
    @Resource private BusinessTaskCommandService taskService;

    @Override @TenantJob
    @org.springframework.transaction.annotation.Transactional
    public String execute(String param) {
        cycles.monitor();
        int processed = 0;
        for (StudentDeliveryStageDO stage : stageMapper.selectDueWaiting(LocalDateTime.now(), 200)) {
            if ("S6".equals(stage.getStageCode())) continue;
            var plan=plans.selectById(stage.getPlanId()); if(plan==null || !"ACTIVE".equals(plan.getStatus())) continue;
            if (!stageMapper.claim(stage.getId(), stage.getTriggerAt())) continue;
            String key = "student-delivery:" + stage.getPlanId() + ":" + stage.getStageCode();
            taskService.create(new BusinessTaskCreateCommand(
                    "student_delivery_confirmation", "student_delivery_stage", stage.getId(), stage.getDirectorUserId(),
                    "填写" + stage.getStageCode() + "期交付确认", "学员账号交付确认待填写", "STUDENT_DELIVERY_CONFIRM",
                    stage.getTriggerAt(), stage.getTriggerAt(), "{\"accountId\":" + stage.getAccountId() + ",\"stageId\":" + stage.getId() + ",\"planId\":" + stage.getPlanId() + ",\"stageCode\":\"" + stage.getStageCode() + "\"}", key));
            processed++;
        }
        return "学员交付确认阶段任务：处理 " + processed + " 条";
    }
}
