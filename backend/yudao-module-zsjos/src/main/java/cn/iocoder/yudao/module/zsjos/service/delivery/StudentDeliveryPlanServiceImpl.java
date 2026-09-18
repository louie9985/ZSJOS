package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Service @RequiredArgsConstructor
public class StudentDeliveryPlanServiceImpl implements StudentDeliveryPlanService {
    private final StudentDeliveryPlanMapper planMapper;
    private final StudentDeliveryStageMapper stageMapper;
    private final StudentDeliveryConfigMapper configMapper;

    @Override @Transactional
    public StudentDeliveryPlanDO ensurePlan(Long studentPersonId, Long accountId, Long serviceRelationId,
                                            Long directorUserId, LocalDateTime accountOpenedAt) {
        StudentDeliveryPlanDO existing = planMapper.selectOne(new LambdaQueryWrapper<StudentDeliveryPlanDO>()
                .eq(StudentDeliveryPlanDO::getAccountId, accountId)
                .eq(StudentDeliveryPlanDO::getStatus, "ACTIVE").last("LIMIT 1"));
        if (existing != null) return existing;
        StudentDeliveryPlanDO plan = new StudentDeliveryPlanDO().setStudentPersonId(studentPersonId)
                .setAccountId(accountId).setServiceRelationId(serviceRelationId).setDirectorUserId(directorUserId)
                .setAccountOpenedAt(accountOpenedAt).setRoundNo(1).setConfigVersion(1).setStatus("ACTIVE").setVersion(0);
        planMapper.insert(plan);
        var config = configMapper.selectOne(new LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO>()
                .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled, true)
                .orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getVersion).last("LIMIT 1"));
        Map<String, Integer> intervals = config == null ? Map.of() : Map.of(
                "S0", config.getS0Days(), "S1", config.getS1Days(), "S2", config.getS2Days(),
                "S3", config.getS3Days(), "S4", config.getS4Days(), "S5", config.getS5Days(), "S6", config.getS6Days());
        StudentDeliveryStageDO stage = new StudentDeliveryStageDO().setPlanId(plan.getId()).setAccountId(accountId)
                .setStageCode("S0").setDirectorUserId(directorUserId)
                .setTriggerAt(StudentDeliverySchedule.next("S0", accountOpenedAt, intervals))
                .setStatus("WAITING").setVersion(0);
        stageMapper.insert(stage);
        return plan;
    }

    @Override @Transactional
    public void createNextStages(Long planId, Long accountId, Long directorUserId, String completedStage,
                                 LocalDateTime completedAt) {
        var config = configMapper.selectOne(new LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO>()
                .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled, true)
                .orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getVersion).last("LIMIT 1"));
        Map<String, Integer> intervals = config == null ? Map.of() : Map.of("S0", config.getS0Days(), "S1", config.getS1Days(), "S2", config.getS2Days(), "S3", config.getS3Days(), "S4", config.getS4Days(), "S5", config.getS5Days(), "S6", config.getS6Days());
        Map<String, LocalDateTime> nextStages = StudentDeliveryStagePlanner.afterCompletion(completedStage, completedAt, intervals);
        if (StudentDeliverySchedule.PARALLEL_AFTER_S2.contains(completedStage) && stageMapper.selectCount(new LambdaQueryWrapper<StudentDeliveryStageDO>()
                .eq(StudentDeliveryStageDO::getPlanId, planId).in(StudentDeliveryStageDO::getStageCode, StudentDeliverySchedule.PARALLEL_AFTER_S2)
                .eq(StudentDeliveryStageDO::getStatus, "COMPLETED")) == 3) nextStages.put("S6", completedAt);
        nextStages.forEach((stageCode, triggerAt) -> {
            long count = stageMapper.selectCount(new LambdaQueryWrapper<StudentDeliveryStageDO>().eq(StudentDeliveryStageDO::getPlanId, planId).eq(StudentDeliveryStageDO::getStageCode, stageCode));
            if (count == 0) stageMapper.insert(new StudentDeliveryStageDO().setPlanId(planId).setAccountId(accountId).setStageCode(stageCode).setDirectorUserId(directorUserId).setTriggerAt(triggerAt).setStatus("S6".equals(stageCode) ? "MONITORING" : "WAITING").setVersion(0));
        });
    }
}
