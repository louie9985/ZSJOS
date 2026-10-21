package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchItemDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.*;

@Component
public class ContentReviewNotifyPublisher {

    private static final String BIZ_TYPE = "content_review";

    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;
    @Resource private ContentReviewBatchMapper batchMapper;
    @Resource private cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi processTaskApi;
    @Resource private ContentReviewBatchItemMapper itemMapper;
    @Resource private ApplicationEventPublisher eventPublisher;

    public void publishBatchSubmitted(Long batchId, Long operatorUserId, LocalDateTime occurredAt) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) return;

        Set<Long> directorUserIds = resolveDirectorUserIds(batch);
        Set<Long> operatorUserIds = resolveOperatorUserIds(batch);

        publish("zsjos.content_review.batch_submitted", batchId,
                "batch:" + batchId + ":submitted",
                operatorUserId, occurredAt,
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "itemCount", countItems(batchId),
                        "operatorName", "运营",
                        "directorUserIds", directorUserIds,
                        "operatorUserIds", operatorUserIds
                ));
    }

    public record NodeResult(Long tenantId, Long batchId, String processInstanceId, String taskId,
                             String stage, boolean approved, Long reviewerUserId, String reason,
                             LocalDateTime occurredAt) {}

    public void deferNodeResult(ContentReviewBatchDO batch, String stage,
                                BpmTaskActionContext context) {
        // The common BPM validator runs before engine completion. BEFORE_COMMIT observes the actual
        // next assignees and shares the approval transaction, including material-collection rollback.
        eventPublisher.publishEvent(new NodeResult(TenantContextHolder.getRequiredTenantId(), batch.getId(),
                context.getProcessInstanceId(), context.getTaskId(), stage, "APPROVE".equals(context.getAction()),
                context.getUserId(), context.getReason(), LocalDateTime.now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void publishNodeResult(NodeResult result) {
        TenantUtils.execute(result.tenantId(), () -> {
            ContentReviewBatchDO batch = batchMapper.selectById(result.batchId());
            if (batch == null || !Objects.equals(batch.getProcessInstanceId(), result.processInstanceId())) {
                throw new IllegalStateException("Content approval notification round changed");
            }
            List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batch.getId());
            boolean director = STAGE_DIRECTOR.equals(result.stage());
            long returnedCount = items.stream().filter(item -> DECISION_RETURNED.equals(
                    director ? item.getDirectorDecision() : item.getFinalDecision())).count();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("batchNo", batch.getBatchNo());
            payload.put("batchTitle", buildBatchTitle(batch));
            payload.put("itemCount", items.size());
            payload.put("returnedCount", returnedCount);
            payload.put("reviewerName", director ? "编导" : "终审");
            payload.put("reviewStage", result.stage());
            payload.put("decision", result.approved() ? DECISION_APPROVED : DECISION_RETURNED);
            payload.put("nextStatus", result.approved() ? director ? "待终审" : "待发布" : "已退回修改");
            payload.put("reason", result.reason() == null ? "" : result.reason());
            payload.put("operatorUserIds", resolveOperatorUserIds(batch));
            payload.put("directorUserIds", resolveDirectorUserIds(batch));
            payload.put("submitterUserId", batch.getCreator());
            if (director && result.approved()) payload.put("finalReviewerUserIds", resolveFinalReviewerUserIds(batch));
            payload.put("deepLink", "/zsjos/material-library/content-review?batchId=" + batch.getId());
            String scene = "zsjos.content_review." + (director ? "director" : "final")
                    + (result.approved() ? "_completed" : "_rejected");
            publish(scene, batch.getId(), "content-review:" + batch.getId() + ":"
                    + result.processInstanceId() + ":" + result.taskId() + ":result",
                    result.reviewerUserId(), result.occurredAt(), payload);
        });
    }

    public void publishReviewTimeout(ContentReviewBatchDO batch,
            cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO task,
            cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO rule, LocalDateTime now) {
        if (task.assigneeUserId() == null) return;
        boolean director = BATCH_DIRECTOR_REVIEW.equals(batch.getStatus());
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", batch.getBatchNo());
        payload.put("batchTitle", buildBatchTitle(batch));
        payload.put("pendingHours", java.time.Duration.between(task.createTime(), now).toHours());
        payload.put("directorUserIds", director ? Set.of(task.assigneeUserId()) : Set.of());
        payload.put("finalReviewerUserIds", director ? Set.of() : Set.of(task.assigneeUserId()));
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode("zsjos.content_review.review_timeout_reminder")
                .sourceEventKey("content-review-timeout:" + task.id() + ":" + rule.getId())
                .targetRuleId(rule.getId()).bizType(BIZ_TYPE).bizId(batch.getId()).occurredAt(now).payload(payload).build());
    }

    private void publish(String sceneCode, Long batchId, String sourceEventKey, Long operatorUserId,
                        LocalDateTime occurredAt, Map<String, Object> context) {
        Map<String, Object> payload = new LinkedHashMap<>(context);
        if (operatorUserId != null) {
            payload.put("operatorUserId", operatorUserId);
        }
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(sceneCode)
                .sourceEventKey(sourceEventKey)
                .bizType(BIZ_TYPE)
                .bizId(batchId)
                .operatorUserId(operatorUserId)
                .occurredAt(occurredAt)
                .payload(payload)
                .build());
    }

    private Set<Long> resolveDirectorUserIds(ContentReviewBatchDO batch) {
        if (batch.getDirectorUserId() != null && batch.getDirectorUserId() > 0) {
            return Set.of(batch.getDirectorUserId());
        }
        return Set.of();
    }

    private Set<Long> resolveOperatorUserIds(ContentReviewBatchDO batch) {
        if (batch.getOperatorUserId() != null && batch.getOperatorUserId() > 0) {
            return Set.of(batch.getOperatorUserId());
        }
        return Set.of();
    }

    private Set<Long> resolveFinalReviewerUserIds(ContentReviewBatchDO batch) {
        if (batch.getProcessInstanceId() == null) return Set.of();
        var snapshot = batch.getContextSnapshotJson() == null ? Map.of()
                : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(batch.getContextSnapshotJson(), Map.class);
        String taskKey = String.valueOf(snapshot.getOrDefault("finalTaskKey", ContentReviewConfigService.FINAL_TASK_KEY));
        return processTaskApi.getPendingTasks(batch.getProcessInstanceId()).stream()
                .filter(task -> taskKey.equals(task.taskDefinitionKey()))
                .map(cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO::assigneeUserId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
    }

    private String buildBatchTitle(ContentReviewBatchDO batch) {
        return batch.getBatchNo() + " 内容审核批次";
    }

    private int countItems(Long batchId) {
        return itemMapper.selectByBatchId(batchId).size();
    }
}
