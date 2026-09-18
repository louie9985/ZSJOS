package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import cn.iocoder.yudao.module.zsjos.service.task.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryErrors.*;

@Service
public class StudentDeliveryCycleService {
    @Resource private DeliveryPositioningSyncService sync;
    @Resource private StudentDeliveryPlanService planService;
    @Resource private StudentDeliveryPlanMapper plans;
    @Resource private StudentDeliveryStageMapper stages;
    @Resource private MediaAccountMapper accounts;
    @Resource private PartnerStudentLinkMapper links;
    @Resource private LeadMapper leads;
    @Resource private BusinessEventMapper events;
    @Resource private MediaWorkflowEventService eventService;
    @Resource private BusinessTaskCommandService tasks;
    @Resource private MediaAccountObjectPermissionProvider objects;
    @Resource private DeliveryPositioningSource sources;
    @Resource @Lazy private PositioningCardService cards;

    public record Reminder(Long accountId, Long stageId, String stageCode, String accountName, LocalDateTime dueAt, Long weeklyLeads) {}
    public static LocalDateTime weekStart(LocalDate today) { return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(); }
    public Long weeklyLeads(Long accountId) {
        var account = accounts.selectById(accountId);
        if (account == null || account.getStudentPersonId() == null) return null;
        var link = links.selectActiveByStudent(account.getStudentPersonId()); if (link == null) return null;
        var end = weekStart(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        return leads.countDeliveryWeeklyLeads(TenantContextHolder.getRequiredTenantId(),link.getPartnerId(),end.minusWeeks(1),end);
    }

    /** Existing tenant job owns task creation; passive views never advance business state. */
    @Transactional(rollbackFor=Exception.class)
    public void monitor() {
        for (var account : accounts.selectList()) sync.syncAccount(account.getId());
        for (var plan : plans.selectList(new LambdaQueryWrapper<StudentDeliveryPlanDO>().eq(StudentDeliveryPlanDO::getStatus,"ACTIVE"))) {
            var account = accounts.selectById(plan.getAccountId()); if (account == null) continue;
            var parallel=stages.selectList(new LambdaQueryWrapper<StudentDeliveryStageDO>().eq(StudentDeliveryStageDO::getPlanId,plan.getId())
                    .in(StudentDeliveryStageDO::getStageCode,StudentDeliverySchedule.PARALLEL_AFTER_S2).eq(StudentDeliveryStageDO::getStatus,"COMPLETED"));
            boolean ready=parallel.size()==3;
            if(ready && parallel.stream().allMatch(s -> s.getCompletedAt()!=null)) planService.createNextStages(plan.getId(),plan.getAccountId(),account.getDirectorUserId(),"S5",
                    parallel.stream().map(StudentDeliveryStageDO::getCompletedAt).max(LocalDateTime::compareTo).orElseThrow());
            for (var stage : stages.selectList(new LambdaQueryWrapper<StudentDeliveryStageDO>().eq(StudentDeliveryStageDO::getPlanId,plan.getId()))) {
                if (!Objects.equals(stage.getDirectorUserId(),account.getDirectorUserId())) {
                    stage.setDirectorUserId(account.getDirectorUserId()); stages.updateById(stage);
                    tasks.reassignPending(List.of("student_delivery_confirmation"),stage.getId(),account.getDirectorUserId());
                }
                if ("S6".equals(stage.getStageCode()) && Set.of("WAITING","PENDING","OVERDUE").contains(stage.getStatus())) {
                    if (!ready) { tasks.cancel("student_delivery_confirmation",stage.getId(),stage.getDirectorUserId(),LocalDateTime.now(),"等待S3至S5全部确认"); continue; }
                    stage.setStatus("MONITORING").setVersion(stage.getVersion()+1); stages.updateById(stage);
                }
                if (!"MONITORING".equals(stage.getStatus())) continue;
                Long count = weeklyLeads(account.getId());
                String key = "delivery-week:"+stage.getId()+":"+weekStart(LocalDate.now(ZoneId.of("Asia/Shanghai"))).toLocalDate();
                var previousEnd=weekStart(LocalDate.now(ZoneId.of("Asia/Shanghai"))).minusWeeks(1).toLocalDate();
                tasks.completeByKey("delivery-week:"+stage.getId()+":"+previousEnd,LocalDateTime.now());
                if (count != null && count < 5) tasks.create(new BusinessTaskCreateCommand("student_delivery_confirmation","student_delivery_stage",stage.getId(),stage.getDirectorUserId(),
                        "S6上周有效客资低于5条", "确认继续观察或结束本期重新定位", "STUDENT_DELIVERY_CONFIRM",LocalDateTime.now(),LocalDateTime.now(),
                        JsonUtils.toJsonString(Map.of("accountId",account.getId(),"stageId",stage.getId(),"planId",plan.getId(),"stageCode","S6")),key));
                else tasks.cancel("student_delivery_confirmation",stage.getId(),stage.getDirectorUserId(),LocalDateTime.now(),"本周监测条件已解除或来源缺失");
            }
        }
    }

    public List<Reminder> reminders(Long userId) {
        var result = new ArrayList<Reminder>(); var now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        for(var stage:stages.selectList(new LambdaQueryWrapper<StudentDeliveryStageDO>().eq(StudentDeliveryStageDO::getDirectorUserId,userId).in(StudentDeliveryStageDO::getStatus,List.of("PENDING","OVERDUE","MONITORING")))) {
            if (!objects.hasPermission(stage.getAccountId(),"edit",userId)) continue;
            var plan=plans.selectById(stage.getPlanId()); if(plan==null || !"ACTIVE".equals(plan.getStatus())) continue;
            Long count="MONITORING".equals(stage.getStatus())?weeklyLeads(stage.getAccountId()):null;
            if ("MONITORING".equals(stage.getStatus()) ? count==null || count>=5 : stage.getDueAt()==null || stage.getDueAt().isAfter(now)) continue;
            if(events.selectByIdempotencyKey(reminderKey(stage.getId(),userId))!=null) continue;
            var account=accounts.selectById(stage.getAccountId());
            result.add(new Reminder(stage.getAccountId(),stage.getId(),stage.getStageCode(),account.getNickname(),stage.getDueAt(),count));
        }
        return result;
    }
    private String reminderKey(Long stageId,Long userId) {return "delivery-seen:"+stageId+":"+userId+":"+LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    @Transactional(rollbackFor=Exception.class)
    public void acknowledge(Long userId,List<Long> ids) {
        var allowed=reminders(userId).stream().map(Reminder::stageId).collect(java.util.stream.Collectors.toSet());
        for(Long id:ids) {
            if(events.selectByIdempotencyKey(reminderKey(id,userId))!=null) continue;
            if(!allowed.contains(id)) throw exception(DEFER_PERMISSION_DENIED);
            eventService.transition("student_delivery_stage",id,userId,null,"REMINDER_SEEN",null,reminderKey(id,userId));
        }
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public Long reposition(Long accountId,Long planId,Integer version,Long userId) {
        var plan=plans.selectById(planId);
        if(plan==null || !Objects.equals(plan.getAccountId(),accountId)) throw exception(CYCLE_INVALID);
        var stage=stages.selectOne(new LambdaQueryWrapper<StudentDeliveryStageDO>().eq(StudentDeliveryStageDO::getPlanId,planId).eq(StudentDeliveryStageDO::getStageCode,"S6"));
        if(stage==null) throw exception(CYCLE_INVALID);
        stage=stages.selectByIdForUpdate(stage.getId(),TenantContextHolder.getRequiredTenantId());
        plan=plans.selectById(planId);
        if(!Objects.equals(stage.getDirectorUserId(),userId)) throw exception(DEFER_PERMISSION_DENIED);
        var source=sources.latest(accounts.selectById(accountId)); if(source==null) throw exception(SOURCE_REQUIRED);
        if("REPOSITIONING".equals(plan.getStatus())) return source.getCardId();
        if(!"ACTIVE".equals(plan.getStatus()) || !"MONITORING".equals(stage.getStatus()) || !Objects.equals(stage.getVersion(),version)) throw exception(DEFER_CONFLICT);
        Long weekly=weeklyLeads(accountId); if(weekly==null || weekly>=5) throw exception(CYCLE_INVALID);
        var card=cards.require(source.getCardId());
        cards.startRevision(card.getId(),card.getVersion(),userId);
        var now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        plan.setStatus("REPOSITIONING").setSourceSubmissionId(source.getId()).setRestartRequestedAt(now); plans.updateById(plan);
        stage.setStatus("COMPLETED").setCompletedAt(now).setCompletedBy(userId).setVersion(version+1);stages.updateById(stage);
        tasks.cancel("student_delivery_confirmation",stage.getId(),userId,now,"本轮结束，等待重新定位");
        return card.getId();
    }
}
