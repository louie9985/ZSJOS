package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessProgressApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo.PointsApprovalRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsRequestMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class PointsApprovalProgressService {
    public static final String URGE_PERMISSION = PointsRequestService.PERM + "urge";
    public static final String URGE_SCENE = "zsjos.points-redemption.approval_urged";
    private static final Duration COOLDOWN = Duration.ofMinutes(30);
    @Resource private PointsRequestMapper requests;
    @Resource private BpmProcessProgressApi progress;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private NotifyBusinessEventApi notifications;
    @Resource private RedissonClient redis;
    @Resource private TransactionTemplate transactions;

    @ZsjosPermission(bizType = "points-redemption", bizId = "#id", action = "read")
    public PointsApprovalRespVO get(Long id, Long userId) {
        var row = require(id);
        var result = new PointsApprovalRespVO();
        result.setAvailability("UNAVAILABLE");
        if (row.getProcessInstanceId() == null) {
            result.setUnavailableReason("审批流程记录缺失，请联系管理员");
            return result;
        }
        try {
            var detail = progress.getProgress(row.getProcessInstanceId(), userId);
            result.setProgress(detail); result.setAvailability("AVAILABLE");
            result.setCanUrge(Objects.equals(row.getApplicantUserId(), userId)
                    && permissions.hasAnyPermissions(userId, URGE_PERMISSION)
                    && "pending".equals(row.getApprovalStatus()) && Objects.equals(detail.status(), 1)
                    && detail.currentTasks().stream().anyMatch(t -> t.assigneeUserId() != null));
            result.setNextUrgeAt(redis.<Long>getBucket(key(id) + ":cooldown").get());
        } catch (RuntimeException ex) {
            result.setAvailability("UNAVAILABLE"); result.setCanUrge(false);
            result.setUnavailableReason("审批进度暂时无法读取，请重试");
        }
        return result;
    }

    @ZsjosPermission(bizType = "points-redemption", bizId = "#id", action = "urge")
    public void urge(Long id, String idempotencyKey, Long userId) {
        var row = require(id);
        if (!Objects.equals(row.getApplicantUserId(), userId) || !permissions.hasAnyPermissions(userId, URGE_PERMISSION))
            throw PointsErrors.denied();
        var prefix = key(id);
        var lock = redis.getLock(prefix + ":lock");
        if (!lock.tryLock()) throw exception(new ErrorCode(1_090_296_008, "催办正在处理中，请稍后重试"));
        try {
            var replay = redis.<Boolean>getBucket(prefix + ":replay:" + DigestUtil.sha256Hex(userId + ":" + idempotencyKey));
            if (Boolean.TRUE.equals(replay.get())) return;
            var cooldown = redis.<Long>getBucket(prefix + ":cooldown");
            Long next = cooldown.get();
            if (next != null && next > System.currentTimeMillis())
                throw exception(new ErrorCode(1_090_296_009, "催办间隔为30分钟，请稍后再试"));
            // Hold the per-request lock through the durable notification transaction commit.
            transactions.executeWithoutResult(status -> publish(id, idempotencyKey, userId));
            cooldown.set(System.currentTimeMillis() + COOLDOWN.toMillis(), COOLDOWN);
            replay.set(true, Duration.ofDays(1));
        } finally { lock.unlock(); }
    }

    void publish(Long id, String idempotencyKey, Long userId) {
        var row = requests.lock(id, TenantContextHolder.getRequiredTenantId());
        if (row == null) throw PointsErrors.invalid("积分申请不存在");
        if (!Objects.equals(row.getApplicantUserId(), userId)) throw PointsErrors.denied();
        if (!"pending".equals(row.getApprovalStatus()) || row.getProcessInstanceId() == null
                || !Objects.equals(progress.getProgress(row.getProcessInstanceId(), userId).status(), 1))
            throw exception(new ErrorCode(1_090_296_010, "审批已结束或尚未启动，无法催办"));
        var current = progress.getCurrentTasks(Set.of(row.getProcessInstanceId())).getOrDefault(row.getProcessInstanceId(), List.of());
        var userMap = users.getUserMap(current.stream().map(BpmProcessProgressDTO.PendingTask::assigneeUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, BpmProcessProgressDTO.PendingTask> recipients = new LinkedHashMap<>();
        current.forEach(task -> {
            var user = task.assigneeUserId() == null ? null : userMap.get(task.assigneeUserId());
            if (user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) recipients.putIfAbsent(user.getId(), task);
        });
        if (recipients.isEmpty()) throw exception(new ErrorCode(1_090_296_011, "当前没有有效的待审批人，无法催办"));
        recipients.forEach((recipient, task) -> {
            int accepted = notifications.publishDurable(NotifyBusinessEvent.builder()
                    .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(URGE_SCENE)
                    .sourceEventKey("points-urge:" + DigestUtil.sha256Hex(id + ":" + userId + ":" + idempotencyKey + ":" + recipient))
                    .bizType("points-redemption").bizId(id).operatorUserId(userId).occurredAt(LocalDateTime.now())
                    .payload(Map.of("requestNo", row.getRequestNo(), "applicantName", row.getApplicantName(),
                            "taskName", task.name(), "taskId", task.id(), "processInstanceId", row.getProcessInstanceId(),
                            "deepLink", "/bpm/task/todo?taskId=" + task.id()))
                    .recipientMode("FIXED").fixedRecipients(List.of(NotifyRecipientDTO.admin(recipient))).build());
            if (accepted == 0) throw exception(new ErrorCode(1_090_296_012, "催办通知未配置或已停用，请联系管理员"));
        });
    }

    private PointsRequestDO require(Long id) {
        var row = requests.selectById(id);
        if (row == null) throw PointsErrors.invalid("积分申请不存在");
        return row;
    }
    private String key(Long id) { return "zsjos:points:urge:" + TenantContextHolder.getRequiredTenantId() + ":" + id; }
}
