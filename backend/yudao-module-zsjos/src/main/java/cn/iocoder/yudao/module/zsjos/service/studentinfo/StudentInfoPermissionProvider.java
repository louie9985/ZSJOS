package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Set;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Component
public class StudentInfoPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private LeadMapper leads;
    @Resource private LeadObjectPermissionService leadPermission;
    @Override public String getBizType() { return "student-info"; }
    @Override public boolean hasPermission(Long id,String action,Long userId) {
        var user=getLoginUser();
        if (user==null || !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())) return false;
        var lead=leads.selectById(id);
        if (lead==null) return false;
        return Set.of("create","link-read","regenerate","revoke").contains(action)
                ? canManage(lead,userId) : Set.of("read","sensitive-read","export").contains(action)
                && leadPermission.canReadDetail(lead,userId);
    }
    public boolean canManage(LeadDO lead,Long userId) {
        return userId!=null && leadPermission.canReadAsOwnerOrManager(lead,userId);
    }
    @Override public void check(Long id,String action,Long userId) {
        if (!hasPermission(id,action,userId)) throw exception(LEAD_PERMISSION_DENIED);
    }
}
