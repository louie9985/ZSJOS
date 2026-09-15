package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassScopeService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;

@Component
public class StudentObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private DeliveryClassScopeService scopeService;
    @Resource private AdminUserApi adminUserApi;
    private boolean hasManagedStudentReadPermission(Long userId) {
        return permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:query",
                DeliveryClassService.PERMISSION_QUERY_MANAGED);
    }

    @Override public String getBizType() { return "student"; }
    @Override public boolean hasPermission(Long bizId, String action, Long userId) {
        if ("repurchase".equals(action)) {
            return !relationMapper.selectOwnedRepurchaseEligibleByPerson(userId, bizId).isEmpty();
        }
        if (!"read".equals(action)) return false;
        if (!relationMapper.selectByOwnerAndPersonIncludingHistory(userId, bizId).isEmpty()) return true;
        if (relationMapper.existsActiveByCollaboratorAndPerson(userId, bizId)) return true;
        if (!hasManagedStudentReadPermission(userId)) return false;
        DeliveryClassScopeService.Scope scope = scopeService.resolve(userId);
        if (scope.allDepartments()) return !relationMapper.selectOwnedByPersonIds(java.util.List.of(bizId), null).isEmpty();
        var ownerIds = new java.util.LinkedHashSet<Long>(); ownerIds.add(userId);
        if (!scope.deptIds().isEmpty()) adminUserApi.getUserListByDeptIds(scope.deptIds()).stream()
                .map(AdminUserRespDTO::getId).filter(java.util.Objects::nonNull).forEach(ownerIds::add);
        return !relationMapper.selectOwnedByOwnerIdsAndPersonIds(ownerIds, java.util.List.of(bizId), null).isEmpty();
    }
    @Override public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}
