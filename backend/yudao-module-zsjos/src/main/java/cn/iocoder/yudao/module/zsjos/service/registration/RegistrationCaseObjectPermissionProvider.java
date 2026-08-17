package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
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

    @Override public String getBizType() { return "registration-case"; }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        RegistrationCaseDO item = caseMapper.selectById(bizId);
        if (item == null) return false;
        return "read".equals(action) || Set.of("update", "complete").contains(action)
                && Set.of(STATUS_PENDING, STATUS_PROCESSING).contains(item.getStatus());
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(REGISTRATION_PERMISSION_DENIED);
    }
}
