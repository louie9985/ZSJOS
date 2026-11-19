package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import java.util.Objects;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Component
public class RegistrationCaseObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private RegistrationCaseMapper caseMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCommandMapper commands;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;

    public boolean canRecover(Long userId) {
        return userId != null && permissions.hasTenantReadAllAccess(userId)
                && permissions.hasAnyPermissions(userId, "zsjos:registration:transfer");
    }

    public boolean canTransfer(RegistrationCaseDO item, Long userId) {
        if (userId == null || !isOpen(item)
                || !permissions.hasAnyPermissions(userId, "zsjos:registration:transfer")) return false;
        if (Objects.equals(item.getOwnerUserId(), userId)) return true;
        if (!canRecover(userId)) return false;
        var owner = item.getOwnerUserId() == null ? null : users.getUser(item.getOwnerUserId());
        return owner == null || !CommonStatusEnum.ENABLE.getStatus().equals(owner.getStatus());
    }

    public boolean isOwner(RegistrationCaseDO item, Long userId) {
        return item != null && userId != null && Objects.equals(item.getOwnerUserId(), userId);
    }

    public boolean isOpen(RegistrationCaseDO item) {
        return item != null && Set.of(STATUS_PENDING, STATUS_PROCESSING).contains(item.getStatus());
    }

    public void checkCase(RegistrationCaseDO item, String action, Long userId) {
        if (!allows(item, action, userId)) throw exception(REGISTRATION_PERMISSION_DENIED);
    }

    private boolean allows(RegistrationCaseDO item, String action, Long userId) {
        if ("transfer".equals(action)) return canTransfer(item, userId);
        if ("transfer-command".equals(action)) {
            // A previous operator can replay only their exact command, checked again under the case lock.
            return canTransfer(item, userId) || item != null && userId != null
                    && permissions.hasAnyPermissions(userId, "zsjos:registration:transfer")
                    && commands.hasTransferByOperator(item.getId(), userId);
        }
        if ("read".equals(action)) return isOwner(item, userId) || canTransfer(item, userId);
        return isOwner(item, userId) && isOpen(item) && Set.of("update", "complete", COMMAND_CLOSE).contains(action);
    }

    @Override public String getBizType() { return "registration-case"; }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        return allows(caseMapper.selectById(bizId), action, userId);
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(REGISTRATION_PERMISSION_DENIED);
    }
}
