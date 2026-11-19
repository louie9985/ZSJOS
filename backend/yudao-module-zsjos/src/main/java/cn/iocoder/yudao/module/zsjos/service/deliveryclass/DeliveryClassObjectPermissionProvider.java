package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_PERMISSION_DENIED;

@Component
public class DeliveryClassObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private DeliveryClassMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private DeliveryClassScopeService scopeService;

    @Override public String getBizType() { return DeliveryClassService.BIZ_TYPE; }

    @Override
    public boolean hasPermission(Long id, String action, Long userId) {
        return hasPermission(mapper.selectById(id), action, userId);
    }

    boolean hasPermission(DeliveryClassDO row, String action, Long userId) {
        if (row == null) return false;
        if ("read".equals(action) && permissionApi.hasTenantReadAllAccess(userId)) return true;
        Set<Long> homerooms = scopeService.salesHomerooms(userId).orElse(null);
        if (homerooms != null) {
            // Sales classes follow the head teacher's current department; the actor always sees their own classes.
            if (row.getHomeroomUserId() == null || !homerooms.contains(row.getHomeroomUserId())) return false;
            if ("read".equals(action) && Objects.equals(row.getHomeroomUserId(), userId))
                return permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_ALL,
                        DeliveryClassService.PERMISSION_QUERY_MANAGED, DeliveryClassService.PERMISSION_QUERY_MY);
            if ("read".equals(action)) return permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:query",
                    DeliveryClassService.PERMISSION_QUERY_ALL, DeliveryClassService.PERMISSION_QUERY_MANAGED);
            return permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED);
        }
        if ("read".equals(action) && permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_ALL)) return true;
        if ("read".equals(action) && DeliveryClassKind.pending(row)) {
            return permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED);
        }
        if ("read".equals(action)) {
            if (Objects.equals(row.getHomeroomUserId(), userId)
                    && permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:query",
                            DeliveryClassService.PERMISSION_QUERY_MANAGED, DeliveryClassService.PERMISSION_QUERY_MY)) return true;
            return permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:query",
                    DeliveryClassService.PERMISSION_QUERY_MANAGED) && scopeService.containsRead(userId, row.getDeptId());
        }
        return permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED)
                && scopeService.contains(userId, row.getDeptId());
    }

    DeliveryClassScopeService.Scope commandScope(Long userId, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO user) {
        return permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED)
                ? scopeService.resolve(userId, user) : new DeliveryClassScopeService.Scope(false, java.util.Set.of());
    }

    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DELIVERY_CLASS_PERMISSION_DENIED);
    }
}
