package cn.iocoder.yudao.module.zsjos.service.media;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewNotifyPublisher;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Rule offsets are administrator-owned; live BPM tasks remain the review deadline/assignee source. */
@Component
@Slf4j
public class MediaNotificationReminderScheduler {
    public static final String INTERVIEW = MEDIA_STUDENT_INTERVIEW_REMINDER;
    public static final String REVIEW = "zsjos.content_review.review_timeout_reminder";
    @Resource private TenantFrameworkService tenants;
    @Resource private MaintenanceModeApi maintenance;
    @Resource private NotifyRuleApi rules;
    @Resource private NotifyBusinessEventApi events;
    @Resource private ServiceRelationMapper relations;
    @Resource private ContentReviewBatchMapper batches;
    @Resource private BpmProcessTaskApi tasks;
    @Resource private ContentReviewNotifyPublisher reviews;

    @Scheduled(fixedDelay = 60_000L)
    public void scan() {
        if (maintenance.isEnabled()) return;
        for (Long tenant : tenants.getTenantIds()) TenantUtils.execute(tenant, () -> {
            try { emit(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))); }
            catch (RuntimeException ex) { log.error("Media reminder scan failed for tenant {}", tenant, ex); }
        });
    }

    public void emit(LocalDateTime now) {
        var enabled = rules.getEnabledTimingRules(List.of(INTERVIEW, REVIEW));
        if (enabled.stream().anyMatch(r -> INTERVIEW.equals(r.getSceneCode()))) {
            long after = 0;
            while (true) {
                var page = relations.selectList(new LambdaQueryWrapper<ServiceRelationDO>()
                        .gt(ServiceRelationDO::getId, after).eq(ServiceRelationDO::getStatus, "active")
                        .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                        .eq(ServiceRelationDO::getDirectorStage, "positioning_interview")
                        .isNotNull(ServiceRelationDO::getDirectorInterviewAt).orderByAsc(ServiceRelationDO::getId).last("LIMIT 200"));
                for (var relation : page) for (var rule : enabled) {
                    if (!INTERVIEW.equals(rule.getSceneCode()) || !due(relation.getDirectorInterviewAt(), rule, now)) continue;
                    Set<Long> ids = new LinkedHashSet<>(Arrays.asList(relation.getContentDirectorUserId(), relation.getOperatorUserId()));
                    ids.remove(null);
                    if (ids.isEmpty()) continue;
                    events.publish(NotifyBusinessEvent.builder().tenantId(TenantContextHolder.getRequiredTenantId())
                            .sceneCode(INTERVIEW).targetRuleId(rule.getId()).bizType("student_service").bizId(relation.getId())
                            .sourceEventKey("interview-reminder:" + relation.getId() + ":" + relation.getDirectorInterviewAt() + ":" + rule.getId())
                            .occurredAt(now).payload(Map.of("assigneeUserIds", ids, "interviewAt", relation.getDirectorInterviewAt())).build());
                }
                if (page.size() < 200) break;
                after = page.getLast().getId();
            }
        }
        if (enabled.stream().noneMatch(r -> REVIEW.equals(r.getSceneCode()))) return;
        long after = 0;
        while (true) {
            var page = batches.selectList(new LambdaQueryWrapper<ContentReviewBatchDO>().gt(ContentReviewBatchDO::getId, after)
                    .in(ContentReviewBatchDO::getStatus, List.of("DIRECTOR_REVIEW", "FINAL_REVIEW"))
                    .orderByAsc(ContentReviewBatchDO::getId).last("LIMIT 200"));
            for (var batch : page) {
                var snapshot = batch.getContextSnapshotJson() == null ? Map.of() : JsonUtils.parseObject(batch.getContextSnapshotJson(), Map.class);
                boolean director = "DIRECTOR_REVIEW".equals(batch.getStatus());
                String key = String.valueOf(snapshot.getOrDefault(director ? "directorTaskKey" : "finalTaskKey",
                        director ? ContentReviewConfigService.DIRECTOR_TASK_KEY : ContentReviewConfigService.FINAL_TASK_KEY));
                for (var task : tasks.getPendingTasks(batch.getProcessInstanceId())) for (var rule : enabled) {
                    if (REVIEW.equals(rule.getSceneCode()) && key.equals(task.taskDefinitionKey())
                            && task.createTime() != null && "overdue".equals(rule.getTimingStage()) && due(task.createTime(), rule, now))
                        reviews.publishReviewTimeout(batch, task, rule, now);
                }
            }
            if (page.size() < 200) break;
            after = page.getLast().getId();
        }
    }

    static boolean due(LocalDateTime anchor, NotifyTimingRuleRespDTO rule, LocalDateTime now) {
        int offset = rule.getTimingOffsetMinutes() == null ? 0 : Math.max(0, rule.getTimingOffsetMinutes());
        LocalDateTime trigger = "advance".equals(rule.getTimingStage()) ? anchor.minusMinutes(offset)
                : "overdue".equals(rule.getTimingStage()) ? anchor.plusMinutes(offset) : anchor;
        return !now.isBefore(trigger) && (!"advance".equals(rule.getTimingStage()) || now.isBefore(anchor));
    }
}
