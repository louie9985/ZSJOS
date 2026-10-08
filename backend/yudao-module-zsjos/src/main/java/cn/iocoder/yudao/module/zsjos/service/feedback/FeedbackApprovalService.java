package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessProgressApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderHistoryMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.*;

@Service
public class FeedbackApprovalService {
    public static final String URGE_PERMISSION = "zsjos:feedback:requirement:urge";
    public static final String URGE_SCENE = "zsjos.feedback.approval_urged";
    @Resource private FeedbackMapper feedbackMapper;
    @Resource private FeedbackRoundMapper roundMapper;
    @Resource private WorkOrderHistoryMapper historyMapper;
    @Resource private BpmProcessProgressApi progressApi;
    @Resource private FeedbackDynamicFormService formService;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi userApi;
    @Resource private NotifyBusinessEventApi notifyApi;

    /** The caller has already applied list scope. No full workflow query is made per row. */
    public List<FeedbackRespVO> enrich(List<FeedbackRespVO> rows) {
        Set<String> ids = rows.stream().filter(r -> TYPE_REQUIREMENT.equals(r.getFeedbackType())
                && STATUS_APPROVING.equals(r.getStatus())).map(FeedbackRespVO::getProcessInstanceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, List<BpmProcessProgressDTO.PendingTask>> tasks;
        try { tasks = progressApi.getCurrentTasks(ids); }
        catch (RuntimeException ex) {
            rows.stream().filter(r -> TYPE_REQUIREMENT.equals(r.getFeedbackType()) && STATUS_APPROVING.equals(r.getStatus()))
                    .forEach(r -> r.setApprovalSummary(new FeedbackApprovalRespVO.Summary("UNAVAILABLE", List.of())));
            return rows;
        }
        for (var row : rows) {
            if (TYPE_REQUIREMENT.equals(row.getFeedbackType()) && STATUS_APPROVING.equals(row.getStatus())) {
                row.setApprovalSummary(new FeedbackApprovalRespVO.Summary(row.getProcessInstanceId() == null
                        ? "UNAVAILABLE" : "AVAILABLE", row.getProcessInstanceId() == null ? List.of() : tasks.getOrDefault(row.getProcessInstanceId(), List.of())));
            }
        }
        return rows;
    }

    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "read-own")
    public FeedbackApprovalRespVO getOwn(Long id, Integer roundNo, Long userId) {
        return read(id, roundNo, userId, false, false);
    }

    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "read-admin")
    public FeedbackApprovalRespVO getAdmin(Long id, Integer roundNo, Long userId) {
        return read(id, roundNo, userId, true, false);
    }

    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "read-approver")
    public FeedbackApprovalRespVO getApprover(Long id, Integer roundNo, Long userId) {
        return read(id, roundNo, userId, true, true);
    }

    private FeedbackApprovalRespVO read(Long id, Integer roundNo, Long userId, boolean readOnly, boolean approver) {
        FeedbackDO row = require(id, false);
        List<FeedbackRoundDO> rounds = roundMapper.selectByFeedbackId(id).stream()
                .filter(r -> !approver || canReadRound(r, userId)).toList();
        if (rounds.isEmpty()) throw exception(FEEDBACK_APPROVAL_ROUND_INVALID);
        FeedbackRoundDO round = roundNo == null ? rounds.getLast() : rounds.stream()
                .filter(r -> Objects.equals(r.getRoundNo(), roundNo)).findFirst()
                .orElseThrow(() -> exception(FEEDBACK_APPROVAL_ROUND_INVALID));
        var result = new FeedbackApprovalRespVO();
        result.setRoundNo(round.getRoundNo()); result.setLatestRoundNo(row.getApprovalRoundNo());
        result.setVersion(row.getVersion());
        result.setRounds(rounds.stream().map(r -> new FeedbackApprovalRespVO.Round(r.getRoundNo(), r.getStatus(), r.getSubmittedAt())).toList());
        result.setFields(formService.parseSnapshot(round.getFormSnapshotJson()));
        result.setValues(formService.readDisplayValues(round.getValueSnapshotJson(), result.getFields()));
        result.setLastUrgedAt(round.getLastUrgedAt());
        result.setNextUrgeAt(round.getLastUrgedAt() == null ? null : round.getLastUrgedAt().plusMinutes(30));
        if (round.getProcessInstanceId() == null) {
            boolean noApproval = Boolean.FALSE.equals(FeedbackApprovalContext.parse(round).get("approvalEnabled"));
            result.setAvailability(noApproval ? "NOT_REQUIRED" : "UNAVAILABLE");
            result.setUnavailableReason(noApproval ? "本轮无需审批" : "本轮审批流程记录缺失，请联系管理员");
            return result;
        }
        try {
            var progress = progressApi.getProgress(round.getProcessInstanceId(), userId);
            result.setProgress(progress); result.setAvailability("AVAILABLE");
            result.setCanUrge(!readOnly && SUBJECT_ADMIN.equals(row.getSubmitterSubjectType())
                    && Objects.equals(row.getSubmitterUserId(), userId)
                    && permissionApi.hasAnyPermissions(userId, URGE_PERMISSION)
                    && Objects.equals(round.getRoundNo(), row.getApprovalRoundNo())
                    && STATUS_APPROVING.equals(row.getStatus()) && Objects.equals(progress.status(), 1)
                    && progress.currentTasks().stream().anyMatch(t -> t.assigneeUserId() != null));
        } catch (RuntimeException ex) {
            result.setAvailability("UNAVAILABLE");
            result.setUnavailableReason("审批流程暂时无法读取，请重试；若持续失败，请联系管理员");
        }
        return result;
    }

    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "urge-own")
    @Transactional(rollbackFor = Exception.class)
    public void urge(Long id, FeedbackActionVO.UrgeReq request, Long userId) {
        FeedbackDO row = require(id, true);
        if (!SUBJECT_ADMIN.equals(row.getSubmitterSubjectType()) || !Objects.equals(row.getSubmitterUserId(), userId)
                || !permissionApi.hasAnyPermissions(userId, URGE_PERMISSION)) throw exception(FEEDBACK_PERMISSION_DENIED);
        String fingerprint = DigestUtil.sha256Hex(id + ":" + userId + ":" + request.getRoundNo() + ":" + request.getVersion());
        var replay = historyMapper.selectByOrderAndKey(row.getWorkOrderId(), request.getIdempotencyKey());
        if (replay != null) {
            if (!"APPROVAL_URGE".equals(replay.getOperation()) || !fingerprint.equals(replay.getRequestFingerprint()))
                throw exception(FEEDBACK_IDEMPOTENCY_CONFLICT);
            return;
        }
        if (!Objects.equals(row.getVersion(), request.getVersion())) throw exception(FEEDBACK_VERSION_CONFLICT);
        if (!Objects.equals(row.getApprovalRoundNo(), request.getRoundNo())) throw exception(FEEDBACK_APPROVAL_ROUND_INVALID);
        if (!TYPE_REQUIREMENT.equals(row.getFeedbackType()) || !STATUS_APPROVING.equals(row.getStatus())
                || row.getProcessInstanceId() == null) throw exception(FEEDBACK_URGE_NOT_RUNNING);
        var round = roundMapper.selectByFeedbackId(id).stream().filter(r -> Objects.equals(r.getRoundNo(), request.getRoundNo()))
                .findFirst().orElseThrow(() -> exception(FEEDBACK_APPROVAL_ROUND_INVALID));
        if (!Objects.equals(row.getProcessInstanceId(), round.getProcessInstanceId())) throw exception(FEEDBACK_APPROVAL_ROUND_INVALID);
        LocalDateTime now = LocalDateTime.now();
        if (round.getLastUrgedAt() != null && now.isBefore(round.getLastUrgedAt().plusMinutes(30)))
            throw exception(FEEDBACK_URGE_COOLDOWN, round.getLastUrgedAt().plusMinutes(30));
        var progress = progressApi.getProgress(row.getProcessInstanceId(), userId);
        if (!Objects.equals(progress.status(), 1)) throw exception(FEEDBACK_URGE_NOT_RUNNING);
        // Use a fresh assignment read at command time, not candidate snapshots or client-supplied recipients.
        var current = progressApi.getCurrentTasks(Set.of(row.getProcessInstanceId())).getOrDefault(row.getProcessInstanceId(), List.of());
        var users = userApi.getUserMap(current.stream().map(BpmProcessProgressDTO.PendingTask::assigneeUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, BpmProcessProgressDTO.PendingTask> recipients = new LinkedHashMap<>();
        current.forEach(t -> {
            var user = t.assigneeUserId() == null ? null : users.get(t.assigneeUserId());
            if (user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) recipients.putIfAbsent(user.getId(), t);
        });
        if (recipients.isEmpty()) throw exception(FEEDBACK_URGE_NO_RECIPIENT);
        for (var entry : recipients.entrySet()) {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("feedbackNo", row.getFeedbackNo()); payload.put("feedbackTitle", row.getTitle());
            payload.put("submitterName", row.getSubmitterNameSnapshot()); payload.put("roundNo", round.getRoundNo());
            payload.put("taskId", entry.getValue().id()); payload.put("processInstanceId", row.getProcessInstanceId());
            payload.put("taskName", entry.getValue().name());
            payload.put("deepLink", "/bpm/task/todo?taskId=" + entry.getValue().id());
            int accepted = notifyApi.publishDurable(NotifyBusinessEvent.builder()
                    .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(URGE_SCENE)
                    .sourceEventKey("feedback-urge:" + DigestUtil.sha256Hex(id + ":" + request.getIdempotencyKey() + ":" + entry.getKey()))
                    .bizType("feedback").bizId(id).operatorUserId(userId).occurredAt(now).payload(payload)
                    .recipientMode("FIXED").fixedRecipients(List.of(NotifyRecipientDTO.admin(entry.getKey()))).build());
            if (accepted == 0) throw exception(FEEDBACK_URGE_NOTIFICATION_UNAVAILABLE);
        }
        round.setLastUrgedAt(now);
        if (roundMapper.updateById(round) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        var history = new WorkOrderHistoryDO();
        history.setWorkOrderId(row.getWorkOrderId()); history.setFromStatus(row.getStatus()); history.setToStatus(row.getStatus());
        history.setOperatorSubjectType(SUBJECT_ADMIN); history.setOperatorUserId(userId); history.setOperatedAt(now);
        history.setRoundNo(round.getRoundNo()); history.setReason("提交人催办审批"); history.setOperation("APPROVAL_URGE");
        history.setIdempotencyKey(request.getIdempotencyKey()); history.setRequestFingerprint(fingerprint);
        historyMapper.insert(history);
    }

    private FeedbackDO require(Long id, boolean locked) {
        var row = locked ? feedbackMapper.selectByIdForUpdate(id) : feedbackMapper.selectById(id);
        if (row == null) throw exception(FEEDBACK_NOT_EXISTS);
        return row;
    }

    private boolean canReadRound(FeedbackRoundDO round, Long userId) {
        if (FeedbackApprovalContext.isApprover(FeedbackApprovalContext.parse(round), userId)) return true;
        try { return progressApi.isParticipant(round.getProcessInstanceId(), userId); }
        catch (RuntimeException ex) { return false; }
    }
}
