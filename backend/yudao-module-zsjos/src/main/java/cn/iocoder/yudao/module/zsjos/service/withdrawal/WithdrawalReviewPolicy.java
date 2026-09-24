package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.*;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.WithdrawalMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** The same business authorization protects dedicated and generic BPM decision endpoints. */
@Component
public class WithdrawalReviewPolicy implements BpmBusinessReviewPolicy, BpmTaskActionValidator {
    @Resource private WithdrawalMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi userApi;

    @Override public boolean supports(String processKey, String nodeKey) {
        return PROCESS_DEFINITION_KEY.equals(processKey) && TASK_DEFINITION_KEY.equals(nodeKey);
    }

    @Override public Boolean approvalReasonRequired(BpmTaskActionContext context) {
        return supports(context.getProcessDefinitionKey(), context.getTaskDefinitionKey()) ? false : null;
    }

    @Override public void validate(BpmTaskActionContext context) {
        if (!PROCESS_DEFINITION_KEY.equals(context.getProcessDefinitionKey())) return;
        if (!supports(context.getProcessDefinitionKey(), context.getTaskDefinitionKey())
                || !Objects.equals(context.getTenantId(), String.valueOf(TenantContextHolder.getRequiredTenantId()))) {
            throw exception(WITHDRAWAL_PERMISSION_DENIED);
        }
        if (!ACTION_APPROVE.equals(context.getAction()) && !ACTION_REJECT.equals(context.getAction())) {
            throw exception(WITHDRAWAL_REVIEW_UNSUPPORTED);
        }
        var actor = userApi.getUser(context.getUserId());
        if (actor == null || !Integer.valueOf(0).equals(actor.getStatus())
                || !permissionApi.hasAnyPermissions(context.getUserId(), "zsjos:withdrawal:review")
                || !permissionApi.hasAnyPermissions(context.getUserId(), "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query")) {
            throw exception(WITHDRAWAL_PERMISSION_DENIED);
        }
        var found = mapper.selectByProcessInstanceId(context.getProcessInstanceId());
        if (found == null) throw exception(WITHDRAWAL_NOT_EXISTS);
        // This lock also serializes a legacy generic-BPM decision against cancellation and in-page review.
        var row = mapper.selectByIdForUpdate(found.getId(), TenantContextHolder.getRequiredTenantId());
        if (row == null || !Objects.equals(row.getProcessInstanceId(), context.getProcessInstanceId())
                || !Objects.equals(context.getBusinessKey(), "withdrawal:" + row.getId())) throw exception(WITHDRAWAL_NOT_EXISTS);
        if (!STATUS_PENDING.equals(row.getStatus())) throw exception(WITHDRAWAL_REVIEW_STALE);
        String reason = StrUtil.trim(context.getReason());
        if ((ACTION_REJECT.equals(context.getAction()) && StrUtil.isBlank(reason))
                || (reason != null && reason.length() > 500)) throw exception(WITHDRAWAL_REVIEW_REASON_INVALID);
    }
}
