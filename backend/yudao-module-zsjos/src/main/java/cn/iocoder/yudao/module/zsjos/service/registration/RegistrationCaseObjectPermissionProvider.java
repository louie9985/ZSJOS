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
    @Resource private PermissionApi permissions;

    public boolean isOpen(RegistrationCaseDO item) {
        return item != null && Set.of(STATUS_PENDING, STATUS_PROCESSING).contains(item.getStatus());
    }

    public void checkCase(RegistrationCaseDO item, String action, Long userId) {
        if (!allows(item, action, userId)) throw exception(REGISTRATION_PERMISSION_DENIED);
    }

    private boolean allows(RegistrationCaseDO item, String action, Long userId) {
        if (item == null || userId == null || !permissions.hasAnyPermissions(userId, "zsjos:registration:query-pool")) return false;
        return "read".equals(action) || isOpen(item) && Set.of("update", "complete", COMMAND_CLOSE).contains(action);
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
