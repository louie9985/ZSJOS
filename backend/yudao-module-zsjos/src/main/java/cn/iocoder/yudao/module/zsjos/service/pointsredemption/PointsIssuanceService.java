package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsRequestMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import tools.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.api.RedissonClient;
import java.time.*;
import java.util.*;

@Service
public class PointsIssuanceService {
    @Resource private PointsRequestMapper requests;
    @Resource private PointsPlatformClient platform;
    @Resource private PointsCrypto crypto;
    @Resource private TransactionTemplate transactions;
    @Resource private RedissonClient redis;
    @Resource private BpmProcessTaskApi tasks;
    @Resource private NotifyBusinessEventApi notifications;

    public int drain() {
        var rows = requests.selectList(new LambdaQueryWrapperX<PointsRequestDO>()
                .eq(PointsRequestDO::getApprovalStatus, "approved")
                .in(PointsRequestDO::getIssueStatus, List.of("queued", "issuing", "issued"))
                .eq(PointsRequestDO::getNotified, false)
                .le(PointsRequestDO::getNextAttemptAt, LocalDateTime.now()).orderByAsc(PointsRequestDO::getId).last("LIMIT 50"));
        int count = 0;
        for (var row : rows) { try { issue(row.getId()); count++; } catch (RuntimeException ignored) { /* Persistent rows remain retryable. */ } }
        return count;
    }
    public void issue(Long id) {
        var lock = redis.getLock("zsjos:points:issue:" + TenantContextHolder.getRequiredTenantId() + ":" + id);
        if (!lock.tryLock()) return;
        try {
            PointsRequestDO row = transactions.execute(status -> {
                var current = requests.lock(id, TenantContextHolder.getRequiredTenantId());
                if (current == null || !"approved".equals(current.getApprovalStatus())
                        || "failed".equals(current.getIssueStatus())) return null;
                if ("issued".equals(current.getIssueStatus())) return current;
                platform.requireConfigured(); crypto.validate();
                // The actual recorded human decisions are checked in addition to model validation.
                Set<String> keys = new HashSet<>(JsonUtils.parseArray(current.getTaskKeysJson(), String.class));
                List<String> orderedKeys = JsonUtils.parseArray(current.getTaskKeysJson(), String.class);
                List<Long> approvers = JsonUtils.parseArray(current.getApproverIdsJson(), Long.class);
                var decisions = tasks.getProcessNodeStatuses(current.getProcessInstanceId(), keys);
                if (decisions.size() != keys.size()
                        || !decisions.stream().map(d -> d.getTaskDefinitionKey()).collect(java.util.stream.Collectors.toSet()).equals(keys)
                        || decisions.stream().anyMatch(d -> !"approved".equals(d.getStatus())
                        || d.getActionUserId() == null || Objects.equals(d.getActionUserId(), current.getApplicantUserId())
                        || !keys.contains(d.getTaskDefinitionKey())
                        || !Objects.equals(d.getActionUserId(), approvers.get(orderedKeys.indexOf(d.getTaskDefinitionKey()))))) {
                    current.setIssueStatus("failed"); current.setLastError("审批记录未满足全部人工审批要求"); requests.updateById(current); return null;
                }
                if (current.getPlatformUrl() == null) {
                    current.setPlatformUrl(platform.currentBaseUrl());
                    if (current.getValidDays() != null) current.setExpiresAt(LocalDateTime.now().withNano(0).plusDays(current.getValidDays()));
                }
                if (!Objects.equals(current.getPlatformUrl(), platform.currentBaseUrl())) {
                    current.setIssueStatus("failed"); current.setLastError("平台地址已变更，请恢复原平台后重试"); requests.updateById(current); return null;
                }
                current.setIssueStatus("issuing"); current.setAttempts(current.getAttempts() + 1);
                current.setNextAttemptAt(LocalDateTime.now().plusMinutes(2)); requests.updateById(current); return current;
            });
            if (row == null) return;
            if (!"issued".equals(row.getIssueStatus())) {
                try {
                    JsonNode batch = platform.issue(row);
                    if (!row.getOrderId().equals(batch.path("orderId").asText()) || batch.path("codes").size() != 1)
                        throw new PointsPlatformClient.PlatformFailure("invalid_response", false, 0);
                    JsonNode code = batch.path("codes").get(0);
                    validateCode(code, row);
                    String cipher = crypto.encrypt(code.path("code").asText());
                    transactions.executeWithoutResult(status -> {
                        var latest = requests.lock(id, TenantContextHolder.getRequiredTenantId());
                        latest.setBatchId(batch.path("id").asText()); latest.setExternalCodeId(code.path("id").asText());
                        latest.setCodeCiphertext(cipher); applyStatus(latest, code); latest.setIssueStatus("issued");
                        latest.setLastError(""); latest.setNextAttemptAt(LocalDateTime.now()); requests.updateById(latest);
                    });
                } catch (PointsPlatformClient.PlatformFailure e) {
                    transactions.executeWithoutResult(status -> {
                        var latest = requests.lock(id, TenantContextHolder.getRequiredTenantId());
                        int max = platform.number("max-attempts", 5, 1, 10);
                        latest.setIssueStatus(e.retryable && latest.getAttempts() < max ? "queued" : "failed");
                        latest.setLastError(errorText(e.code));
                        long delay = Math.max(e.retryAfter, 1L << Math.min(latest.getAttempts(), 8));
                        latest.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay)); requests.updateById(latest);
                    }); return;
                }
            }
            transactions.executeWithoutResult(status -> {
                var latest = requests.lock(id, TenantContextHolder.getRequiredTenantId());
                if (Boolean.TRUE.equals(latest.getNotified())) return;
                int accepted = notifications.publishDurable(NotifyBusinessEvent.builder()
                        .tenantId(latest.getTenantId()).sceneCode("zsjos.points-redemption.issued")
                        .sourceEventKey("points-issued:" + id).bizType("points-redemption").bizId(id)
                        .operatorUserId(latest.getApplicantUserId()).occurredAt(LocalDateTime.now())
                        .recipientMode("FIXED").fixedRecipients(List.of(NotifyRecipientDTO.admin(latest.getApplicantUserId())))
                        .payload(Map.of("requestNo", latest.getRequestNo(), "creditsAmount", latest.getCreditsAmount(),
                                "deepLink", "/zsjos/points-redemption?id=" + id)).build());
                latest.setNotified(accepted > 0); latest.setNextAttemptAt(LocalDateTime.now().plusMinutes(5));
                if (accepted == 0) latest.setLastError("兑换码已生成，通知规则尚未启用");
                requests.updateById(latest);
            });
        } catch (RuntimeException error) {
            transactions.executeWithoutResult(status -> {
                var latest = requests.lock(id, TenantContextHolder.getRequiredTenantId());
                if (latest != null && !"issued".equals(latest.getIssueStatus())) {
                    latest.setIssueStatus("failed"); latest.setLastError("配置或处理异常，请核查配置后重试原申请");
                    requests.updateById(latest);
                }
            });
        } finally { lock.unlock(); }
    }
    @ZsjosPermission(bizType="points-redemption", bizId="#id", action="refresh")
    public void refresh(Long id) { updateCode(id, false); }
    @ZsjosPermission(bizType="points-redemption", bizId="#id", action="manage")
    public void disable(Long id) { updateCode(id, true); }
    private void updateCode(Long id, boolean disable) {
        var row = requests.selectById(id);
        if (row == null || !"issued".equals(row.getIssueStatus())) throw PointsErrors.invalid("兑换码尚未生成");
        JsonNode code;
        try { code = platform.code(row, disable); }
        catch (PointsPlatformClient.PlatformFailure error) { throw PointsErrors.invalid(errorText(error.code)); }
        if (!row.getExternalCodeId().equals(code.path("id").asText())) throw PointsErrors.invalid("平台返回了不匹配的兑换码");
        transactions.executeWithoutResult(status -> {
            var latest = requests.lock(id, TenantContextHolder.getRequiredTenantId());
            applyStatus(latest, code); requests.updateById(latest);
        });
    }
    static void validateCode(JsonNode code, PointsRequestDO row) {
        if (code == null || !code.path("id").asText().matches("[A-Za-z0-9-]{1,64}")
                || !code.path("code").asText().matches("[A-Za-z0-9-]{16,200}")
                || !code.path("creditsAmount").isIntegralNumber() || code.path("creditsAmount").asInt() != row.getCreditsAmount()
                || !Set.of("available", "redeemed", "disabled", "expired").contains(code.path("status").asText()))
            throw new PointsPlatformClient.PlatformFailure("invalid_response", false, 0);
        try {
            var expiry = code.path("expiresAt");
            if (row.getExpiresAt() == null ? !expiry.isNull() : expiry.isNull()
                    || !OffsetDateTime.parse(expiry.asText()).toInstant().equals(row.getExpiresAt()
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant()))
                throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new PointsPlatformClient.PlatformFailure("invalid_response", false, 0); }
    }
    private static void applyStatus(PointsRequestDO row, JsonNode code) {
        String state = code.path("status").asText();
        if (!Set.of("available", "redeemed", "disabled", "expired").contains(state)) throw PointsErrors.invalid("平台返回了未知兑换状态");
        // A delayed query must not overwrite an already observed terminal state.
        if ("redeemed".equals(row.getCodeStatus()) || "disabled".equals(row.getCodeStatus()) && "available".equals(state)) return;
        row.setCodeStatus(state); row.setSyncedAt(LocalDateTime.now());
        if (!code.path("redeemedAt").isNull() && !code.path("redeemedAt").isMissingNode())
            row.setRedeemedAt(OffsetDateTime.parse(code.path("redeemedAt").asText()).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime());
    }
    static String errorText(String code) {
        return switch(code) {
            case "unauthorized" -> "平台密钥无效，请更新配置后重试";
            case "not_configured" -> "外部平台尚未配置密钥";
            case "order_conflict" -> "原订单参数冲突，需要人工核查，禁止换单重发";
            case "already_redeemed" -> "兑换码已使用，不能作废";
            case "invalid_request" -> "平台拒绝申请参数或有效期，请人工核查";
            case "not_found" -> "外部平台未找到对应订单或兑换码";
            default -> "平台调用失败（" + code + "）";
        };
    }
}
