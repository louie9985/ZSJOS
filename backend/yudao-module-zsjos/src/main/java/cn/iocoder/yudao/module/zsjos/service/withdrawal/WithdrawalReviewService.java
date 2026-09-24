package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmBusinessReviewApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmBusinessReviewState;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.WithdrawalDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class WithdrawalReviewService {
    @Resource private WithdrawalMapper mapper;
    @Resource private WithdrawalItemMapper itemMapper;
    @Resource private CashbackMapper cashbackMapper;
    @Resource private WithdrawalService withdrawalService;
    @Resource private BpmBusinessReviewApi bpm;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;

    public WithdrawalRespVO enrich(WithdrawalRespVO detail, Long userId) {
        boolean management = permissions.hasAnyPermissions(userId, "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query");
        if (!management) return detail;
        var state = inspect(detail.getId(), detail.getProcessInstanceId());
        detail.setReviewReason(state.getReason());
        detail.setReviewedByName(state.getReviewerName());
        if (detail.getReviewedByName() == null && detail.getReviewedByUserId() != null) {
            var user = users.getUser(detail.getReviewedByUserId());
            if (user != null) detail.setReviewedByName(user.getNickname());
        }
        List<String> actions = new ArrayList<>();
        boolean review = permissions.hasAnyPermissions(userId, "zsjos:withdrawal:review");
        if (STATUS_PENDING.equals(detail.getStatus())) {
            String problem = !review ? "无提现审核权限" : state.getProblem();
            detail.setReviewUnavailableReason(problem);
            if (review && state.isActionable()) actions.addAll(List.of("approve", "reject"));
        } else if (STATUS_APPROVED.equals(detail.getStatus())) {
            if (review) actions.add("reject-approved");
            if (permissions.hasAnyPermissions(userId, "zsjos:withdrawal:payout")) actions.add("payout");
        }
        return detail.setAvailableActions(actions);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "withdrawal", bizId = "#id", action = "review")
    public void decide(Long id, Long userId, WithdrawalReviewReqVO request, boolean approve) {
        WithdrawalDO row = lock(id);
        if (!STATUS_PENDING.equals(row.getStatus()) || !Objects.equals(row.getVersion(), request.getVersion())) {
            throw exception(WITHDRAWAL_REVIEW_STALE);
        }
        String reason = StrUtil.trim(request.getReason());
        if ((!approve && StrUtil.isBlank(reason)) || (reason != null && reason.length() > 500)) {
            throw exception(WITHDRAWAL_REVIEW_REASON_INVALID);
        }
        requireCashbackIntegrity(row);
        bpm.decide(userId, row.getProcessInstanceId(), PROCESS_DEFINITION_KEY, "withdrawal:" + id,
                TASK_DEFINITION_KEY, approve, StrUtil.emptyToNull(reason));
        // The normal BPM result event owns business state. Replay is safe if the event was already delivered.
        var evidence = inspect(id, row.getProcessInstanceId());
        int expected = approve ? BpmProcessInstanceStatusEnum.APPROVE.getStatus() : BpmProcessInstanceStatusEnum.REJECT.getStatus();
        if (!Objects.equals(evidence.getProcessStatus(), expected) || evidence.getEndedAt() == null) {
            throw exception(WITHDRAWAL_REVIEW_REPAIR_INVALID);
        }
        withdrawalService.handleProcessResult(row.getProcessInstanceId(), expected, reason);
        preserveReviewEvidence(id, evidence);
    }

    public BpmBusinessReviewState inspect(Long id, String processId) {
        return bpm.inspect(processId, PROCESS_DEFINITION_KEY, "withdrawal:" + id, TASK_DEFINITION_KEY);
    }

    /** Read-only per-record evidence; exposed only to existing review + management permissions. */
    @ZsjosPermission(bizType = "withdrawal", bizId = "#id", action = "read")
    public Map<String, Object> compatibility(Long id) {
        var row = mapper.selectById(id);
        if (row == null) throw exception(WITHDRAWAL_NOT_EXISTS);
        var state = inspect(id, row.getProcessInstanceId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("withdrawalNo", row.getWithdrawalNo());
        result.put("status", row.getStatus()); result.put("version", row.getVersion());
        result.put("processInstanceId", row.getProcessInstanceId()); result.put("workflow", state);
        String integrity = cashbackProblem(row);
        result.put("cashbackProblem", integrity);
        boolean terminal = state.getEndedAt() != null && Set.of(2, 3).contains(Objects.requireNonNullElse(state.getProcessStatus(), 0));
        boolean repairable = STATUS_PENDING.equals(row.getStatus()) && terminal && state.getReviewerUserId() != null
                && state.getReviewedAt() != null && integrity == null;
        result.put("classification", !STATUS_PENDING.equals(row.getStatus()) ? "NOT_PENDING"
                : integrity != null ? "INTEGRITY_ERROR" : state.isActionable() ? "READY"
                : repairable ? "RESULT_SYNC_REQUIRED" : "MANUAL_REVIEW_REQUIRED");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "withdrawal", bizId = "#id", action = "review")
    public void repair(Long id, Integer version) {
        var row = lock(id);
        var state = inspect(id, row.getProcessInstanceId());
        if (!STATUS_PENDING.equals(row.getStatus())) return; // a previous scoped repair already completed
        if (!Objects.equals(version, row.getVersion())) throw exception(WITHDRAWAL_REVIEW_STALE);
        if (state.getEndedAt() == null || state.getReviewerUserId() == null || state.getReviewedAt() == null
                || !Set.of(2, 3).contains(Objects.requireNonNullElse(state.getProcessStatus(), 0))) {
            throw exception(WITHDRAWAL_REVIEW_REPAIR_INVALID);
        }
        requireCashbackIntegrity(row);
        withdrawalService.handleProcessResult(row.getProcessInstanceId(), state.getProcessStatus(), state.getReason());
        preserveReviewEvidence(id, state);
    }

    private void preserveReviewEvidence(Long id, BpmBusinessReviewState evidence) {
        var updated = lock(id);
        updated.setReviewedByUserId(evidence.getReviewerUserId()).setReviewedAt(evidence.getReviewedAt());
        mapper.updateById(updated);
    }
    private WithdrawalDO lock(Long id) {
        var row = mapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (row == null) throw exception(WITHDRAWAL_NOT_EXISTS);
        return row;
    }
    private void requireCashbackIntegrity(WithdrawalDO row) {
        if (cashbackProblem(row) != null) throw exception(WITHDRAWAL_REVIEW_REPAIR_INVALID);
    }
    private String cashbackProblem(WithdrawalDO row) {
        if (!STATUS_PENDING.equals(row.getStatus())) return null;
        var items = itemMapper.selectByWithdrawalId(row.getId());
        if (items.isEmpty()) return "提现缺少关联返现";
        var total = java.math.BigDecimal.ZERO;
        Set<Long> ids = new HashSet<>();
        for (var item : items) {
            var cashback = cashbackMapper.selectById(item.getCashbackId());
            if (!ids.add(item.getCashbackId()) || !Boolean.TRUE.equals(item.getActiveFlag()) || item.getAmountSnapshot() == null
                    || cashback == null || !"withdrawing".equals(cashback.getStatus())
                    || !Objects.equals(cashback.getPartnerId(), row.getPartnerId())
                    || cashback.getAmount() == null || item.getAmountSnapshot().compareTo(cashback.getAmount()) != 0) return "返现占用关系或金额不一致";
            total = total.add(item.getAmountSnapshot());
        }
        return row.getApplicationAmount() != null && total.compareTo(row.getApplicationAmount()) == 0 ? null : "提现金额与返现合计不一致";
    }
}
