package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoTaskMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;

@Component
public class StudentInfoTaskPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private StudentInfoTaskMapper tasks;
    @Resource private PermissionApi permissions;
    @Override public String getBizType() { return "student-info-task"; }
    public boolean canList(Long userId) {
        var user=getLoginUser();
        return user!=null && UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                && permissions.hasAnyPermissions(userId,"zsjos:student-info-task:query")
                && permissions.hasAnyPermissions(userId,"zsjos:registration:query-pool");
    }
    @Override public boolean hasPermission(Long id,String action,Long userId) {
        return java.util.Set.of("read","create","reuse","link-read","sent","regenerate","revoke").contains(action)
                && canList(userId) && tasks.selectById(id)!=null;
    }
    @Override public void check(Long id,String action,Long userId) {
        if (!hasPermission(id,action,userId)) throw exception(LEAD_PERMISSION_DENIED);
    }
}
