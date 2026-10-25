package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassGroupDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassGroupMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
public class DeliveryClassGroupPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private DeliveryClassGroupMapper mapper;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private DeliveryClassScopeService scope;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper classes;
    @Resource private DeliveryClassObjectPermissionProvider classAuthorization;
    @Override public String getBizType() { return "delivery-class-group"; }
    record MaintainContext(Long department, DeliveryClassScopeService.Scope scope,
                           boolean create, boolean update, boolean delete) {
        boolean can(DeliveryClassGroupDO group, String action) {
            boolean feature = switch (action) { case "create" -> create; case "delete" -> delete; default -> update; };
            return feature && group != null && department != null && Objects.equals(group.getDeptId(), department)
                    && scope != null && (scope.allDepartments() || scope.deptIds().contains(group.getDeptId()));
        }
        boolean any(DeliveryClassGroupDO group) { return can(group, "create") || can(group, "update") || can(group, "delete"); }
    }

    MaintainContext maintenanceContext(Long userId) {
        if (!permissions.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED))
            return new MaintainContext(null, null, false, false, false);
        var user = users.getUser(userId);
        return new MaintainContext(user == null ? null : user.getDeptId(), scope.resolve(userId, user),
                permissions.hasAnyPermissions(userId, "zsjos:delivery-class:create"),
                permissions.hasAnyPermissions(userId, "zsjos:delivery-class:update"),
                permissions.hasAnyPermissions(userId, "zsjos:delivery-class:group-delete"));
    }

    public boolean canMaintain(DeliveryClassGroupDO group, Long userId, String action) {
        return maintenanceContext(userId).can(group, action);
    }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        var group=mapper.selectById(id);
        if(group==null) return false;
        if("read".equals(action)) {
            if(canMaintain(group,userId,"create")||canMaintain(group,userId,"update")||canMaintain(group,userId,"delete")) return true;
            return classes.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO>()
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO::getGroupId,id))
                    .stream().anyMatch(c -> classAuthorization.hasPermission(c.getId(),"read",userId));
        }
        return canMaintain(group, userId, action);
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DeliveryClassGroupErrors.DENIED);
    }
}
