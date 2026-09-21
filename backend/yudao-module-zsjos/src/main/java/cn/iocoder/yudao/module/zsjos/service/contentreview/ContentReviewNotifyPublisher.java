package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchItemDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewRelationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.*;

@Component
public class ContentReviewNotifyPublisher {

    private static final String BIZ_TYPE = "content_review";

    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;
    @Resource private ContentReviewBatchMapper batchMapper;
    @Resource private cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi processTaskApi;
    @Resource private ContentReviewBatchItemMapper itemMapper;
    @Resource private ContentReviewRelationMapper relationMapper;

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

    public void publishDirectorItemDecision(Long batchId, Long itemId, String decision, String comment,
                                            Long reviewerUserId, LocalDateTime occurredAt) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        ContentReviewBatchItemDO item = itemMapper.selectById(itemId);
        if (batch == null || item == null) return;

        Set<Long> operatorUserIds = resolveOperatorUserIds(batch);

        publish("zsjos.content_review.director_item_decision", batchId,
                "batch:" + batchId + ":item:" + itemId + ":director",
                reviewerUserId, occurredAt,
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "itemTitle", "内容" + item.getSortNo(),
                        "decision", decision,
                        "comment", comment != null ? comment : "",
                        "reviewerName", "编导",
                        "operatorUserIds", operatorUserIds
                ));
    }

    public void publishDirectorCompleted(Long batchId, Long reviewerUserId, LocalDateTime occurredAt) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) return;

        Set<Long> operatorUserIds = resolveOperatorUserIds(batch);
        Set<Long> finalReviewerUserIds = resolveFinalReviewerUserIds(batch);

        publish("zsjos.content_review.director_completed", batchId,
                "batch:" + batchId + ":director_completed",
                reviewerUserId, occurredAt,
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "itemCount", countItems(batchId),
                        "reviewerName", "编导",
                        "operatorUserIds", operatorUserIds,
                        "finalReviewerUserIds", finalReviewerUserIds
                ));
    }

    public void publishDirectorRejected(Long batchId, Long reviewerUserId, String reason, LocalDateTime occurredAt) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) return;

        Set<Long> operatorUserIds = resolveOperatorUserIds(batch);

        publish("zsjos.content_review.director_rejected", batchId,
                "batch:" + batchId + ":director_rejected",
                reviewerUserId, occurredAt,
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "reason", reason != null ? reason : "",
                        "reviewerName", "编导",
                        "operatorUserIds", operatorUserIds,
                        "submitterUserId", batch.getCreator()
                ));
    }

    public void publishFinalItemDecision(Long batchId, Long itemId, String decision, String comment,
                                         Long reviewerUserId, LocalDateTime occurredAt) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        ContentReviewBatchItemDO item = itemMapper.selectById(itemId);
        if (batch == null || item == null) return;

        Set<Long> operatorUserIds = resolveOperatorUserIds(batch);
        Set<Long> directorUserIds = resolveDirectorUserIds(batch);

        publish("zsjos.content_review.final_item_decision", batchId,
                "batch:" + batchId + ":item:" + itemId + ":final",
                reviewerUserId, occurredAt,
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "itemTitle", "内容" + item.getSortNo(),
                        "decision", decision,
                        "comment", comment != null ? comment : "",
                        "reviewerName", "终审",
                        "operatorUserIds", operatorUserIds,
                        "directorUserIds", directorUserIds
                ));
    }

    public void publishFinalCompleted(Long batchId, Long reviewerUserId, LocalDateTime occurredAt) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) return;

        Set<Long> operatorUserIds = resolveOperatorUserIds(batch);
        Set<Long> directorUserIds = resolveDirectorUserIds(batch);

        publish("zsjos.content_review.final_completed", batchId,
                "batch:" + batchId + ":final_completed",
                reviewerUserId, occurredAt,
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "itemCount", countItems(batchId),
                        "reviewerName", "终审",
                        "operatorUserIds", operatorUserIds,
                        "directorUserIds", directorUserIds,
                        "submitterUserId", batch.getCreator()
                ));
    }

    public void publishFinalRejected(Long batchId, Long reviewerUserId, String reason, LocalDateTime occurredAt) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) return;

        Set<Long> operatorUserIds = resolveOperatorUserIds(batch);
        Set<Long> directorUserIds = resolveDirectorUserIds(batch);

        publish("zsjos.content_review.final_rejected", batchId,
                "batch:" + batchId + ":final_rejected",
                reviewerUserId, occurredAt,
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "reason", reason != null ? reason : "",
                        "reviewerName", "终审",
                        "operatorUserIds", operatorUserIds,
                        "directorUserIds", directorUserIds,
                        "submitterUserId", batch.getCreator()
                ));
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
