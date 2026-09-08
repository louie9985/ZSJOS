package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

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
        DeliveryClassDO row = mapper.selectById(id);
        if (row == null) return false;
        if ("read".equals(action) && Boolean.TRUE.equals(row.getSystemClass())) {
            return permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED);
        }
        if ("read".equals(action) && Objects.equals(row.getHomeroomUserId(), userId)
                && permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MY)) return true;
        return permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED)
                && scopeService.contains(userId, row.getDeptId());
    }

    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DELIVERY_CLASS_PERMISSION_DENIED);
    }
}
