package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.WithdrawalDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.WithdrawalMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Component
public class WithdrawalObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private WithdrawalMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Override public String getBizType() { return "withdrawal"; }
    @Override public boolean hasPermission(Long bizId, String action, Long userId) {
        try { check(bizId, action, userId); return true; } catch (ServiceException ex) { return false; }
    }
    @Override public void check(Long bizId, String action, Long userId) {
        WithdrawalDO row = mapper.selectById(bizId); if (row == null) throw exception(WITHDRAWAL_NOT_EXISTS);
        boolean allowed = switch (action) {
            case "read-own", "cancel" -> Objects.equals(row.getApplicantUserId(), userId);
            case "read" -> Objects.equals(row.getApplicantUserId(), userId) || permissionApi.hasAnyPermissions(userId,
                    "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query");
            case "finance-read" -> permissionApi.hasAnyPermissions(userId, "zsjos:withdrawal:finance-query");
            case "review" -> permissionApi.hasAnyPermissions(userId, "zsjos:withdrawal:review");
            case "payout" -> permissionApi.hasAnyPermissions(userId, "zsjos:withdrawal:payout");
            default -> false;
        };
        if (!allowed) throw exception(WITHDRAWAL_PERMISSION_DENIED);
    }
}
