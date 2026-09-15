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

    public void publishReviewTimeout(Long batchId, String currentStage, int timeoutHours) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) return;

        Set<Long> targetUserIds = BATCH_DIRECTOR_REVIEW.equals(batch.getStatus())
                ? resolveDirectorUserIds(batch)
                : resolveFinalReviewerUserIds(batch);

        publish("zsjos.content_review.review_timeout", batchId,
                "batch:" + batchId + ":timeout:" + timeoutHours,
                null, LocalDateTime.now(),
                Map.of(
                        "batchNo", batch.getBatchNo(),
                        "batchTitle", buildBatchTitle(batch),
                        "timeoutHours", timeoutHours,
                        "currentStage", currentStage,
                        "directorUserIds", BATCH_DIRECTOR_REVIEW.equals(batch.getStatus()) ? targetUserIds : Set.of(),
                        "finalReviewerUserIds", BATCH_FINAL_REVIEW.equals(batch.getStatus()) ? targetUserIds : Set.of()
                ));
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
        // 终审人需要从流程定义或配置中获取
        // 目前简化处理：如果批次处于终审阶段，从 relationSnapshotJson 中提取
        // 实际场景中可能需要从 BPM 候选人配置或组织架构中动态查询
        if (batch.getProcessInstanceId() == null) return Set.of();

        // TODO: 从 BPM 流程实例中获取终审节点的候选人
        // 或从批次的 relationSnapshotJson 中解析终审人信息
        return Set.of();
    }

    private String buildBatchTitle(ContentReviewBatchDO batch) {
        return batch.getBatchNo() + " 内容审核批次";
    }

    private int countItems(Long batchId) {
        return itemMapper.selectByBatchId(batchId).size();
    }
}
