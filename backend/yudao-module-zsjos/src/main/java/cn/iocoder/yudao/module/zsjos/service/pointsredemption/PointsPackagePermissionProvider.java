package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsPackageMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PointsPackagePermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private PointsPackageMapper packages;
    @Resource private PermissionApi permissions;

    public String getBizType() { return "points-package"; }
    public boolean hasPermission(Long id, String action, Long userId) {
        return id != null && userId != null && ("save".equals(action) || "delete".equals(action))
                && permissions.hasAnyPermissions(userId, PointsRequestService.PERM + ("delete".equals(action) ? "package-delete" : "package-save"))
                && packages.selectById(id) != null;
    }
    public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw PointsErrors.denied();
    }
}
