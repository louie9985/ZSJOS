package cn.iocoder.yudao.module.eam.service.common;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class EamDataScopeService {
    public static final String MANAGE_ALL = "eam:manage-all";
    public static final String ASSET_QUERY_SELF = "eam:asset:query-self";
    public static final String ASSET_QUERY_DEPT = "eam:asset:query-dept";
    public static final String TRANSFER_QUERY_SELF = "eam:transfer:query-self";
    public static final String TRANSFER_QUERY_DEPT = "eam:transfer:query-dept";

    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private HrmEmployeeApi employeeApi;

    public Scope resolve(Long userId, String selfPermission, String deptPermission) {
        if (permissionApi.hasAnyPermissions(userId, MANAGE_ALL)) return Scope.full();
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        Set<Long> deptIds = new HashSet<>();
        if (permissionApi.hasAnyPermissions(userId, deptPermission) && user != null && user.getDeptId() != null) {
            deptIds.add(user.getDeptId());
            deptApi.getChildDeptList(user.getDeptId()).forEach(dept -> deptIds.add(dept.getId()));
        }
        HrmEmployeeRespDTO employee = employeeApi.getEmployeeByUserId(userId);
        return new Scope(false, permissionApi.hasAnyPermissions(userId, selfPermission), deptIds,
                userId, employee == null ? null : employee.getId(), user == null ? null : user.getDeptId());
    }

    public record Scope(boolean all, boolean self, Set<Long> deptIds, Long userId, Long employeeId, Long deptId) {
        public static Scope full() { return new Scope(true, true, Set.of(), null, null, null); }
    }
}
