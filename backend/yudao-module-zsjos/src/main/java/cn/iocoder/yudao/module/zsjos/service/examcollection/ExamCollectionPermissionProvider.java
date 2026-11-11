package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Component
public class ExamCollectionPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private PermissionApi permissions;
    public String getBizType() { return "exam-collection"; }
    public boolean hasPermission(Long id, String action, Long userId) {
        if (id == null || userId == null || action == null || !ACTIONS.contains(action) || TenantContextHolder.getTenantId() == null) return false;
        var task = tasks.selectById(id);
        return task != null && !Boolean.TRUE.equals(task.getDeleted())
                && Objects.equals(task.getTenantId(), TenantContextHolder.getTenantId())
                && permissions.hasAnyPermissions(userId, QUERY)
                && ("query".equals(action) || "export".equals(action) || permissions.hasAnyPermissions(userId, PREFIX + action));
    }
    public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DENIED);
    }
}
