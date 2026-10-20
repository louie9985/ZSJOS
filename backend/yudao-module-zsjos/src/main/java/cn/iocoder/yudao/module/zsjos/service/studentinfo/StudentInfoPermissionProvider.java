package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Set;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Component
public class StudentInfoPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper cases;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper services;
    @Resource private cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceObjectPermissionProvider servicePermission;
    @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permission;
    @Override public String getBizType() { return "student-info"; }
    @Override public boolean hasPermission(Long id,String action,Long userId) {
        var user=getLoginUser();
        if (user==null || !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())) return false;
        var item=cases.selectById(id);
        if (item==null) return false;
        if (!Set.of("create","regenerate","revoke","sent","link-read","read","sensitive-read","export").contains(action)) return false;
        if (Set.of("create","regenerate","revoke","sent").contains(action))
            return Set.of("pending","processing").contains(item.getStatus())
                    && permission.hasAnyPermissions(userId,"zsjos:registration:update","zsjos:registration:query-pool");
        if (permission.hasAnyPermissions(userId,"zsjos:registration:query-pool")) return true;
        if (!Set.of("read","sensitive-read","export").contains(action)) return false;
        return services.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO>()
                .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO::getRegistrationCaseId,id))
                .stream().anyMatch(s -> servicePermission.hasPermission(s.getId(),"read",userId));
    }
    @Override public void check(Long id,String action,Long userId) {
        if (!hasPermission(id,action,userId)) throw exception(LEAD_PERMISSION_DENIED);
    }
}
