package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;

@Component
public class StudentObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ServiceRelationMapper relationMapper;
    @Override public String getBizType() { return "student"; }
    @Override public boolean hasPermission(Long bizId, String action, Long userId) {
        if ("repurchase".equals(action)) {
            return !relationMapper.selectOwnedRepurchaseEligibleByPerson(userId, bizId).isEmpty();
        }
        if (!"read".equals(action)) return false;
        if (!relationMapper.selectByOwnerAndPersonIncludingHistory(userId, bizId).isEmpty()) return true;
        return relationMapper.existsActiveByCollaboratorAndPerson(userId, bizId);
    }
    @Override public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}
