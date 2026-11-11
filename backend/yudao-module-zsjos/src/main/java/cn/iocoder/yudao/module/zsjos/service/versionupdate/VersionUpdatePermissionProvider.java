package cn.iocoder.yudao.module.zsjos.service.versionupdate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.versionupdate.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.versionupdate.VersionUpdateConstants.*;

@Component
public class VersionUpdatePermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private VersionUpdateMapper updates;
    @Resource private VersionUpdateRecipientMapper recipients;
    @Resource private PermissionApi permissions;
    public String getBizType() { return "version-update"; }
    public boolean hasPermission(Long id, String action, Long userId) {
        if (id == null || userId == null || action == null || TenantContextHolder.getTenantId() == null) return false;
        var update = updates.selectById(id);
        if (update == null || Boolean.TRUE.equals(update.getDeleted())
                || !Objects.equals(update.getTenantId(), TenantContextHolder.getTenantId())) return false;
        if ("read".equals(action) || "acknowledge".equals(action)) {
            return permissions.hasAnyPermissions(userId, READ)
                    && (!"acknowledge".equals(action) || permissions.hasAnyPermissions(userId, ACKNOWLEDGE))
                    && recipients.find(id, userId) != null;
        }
        return Set.of("query", "update", "publish", "offline").contains(action)
                && permissions.hasAnyPermissions(userId, QUERY)
                && permissions.hasAnyPermissions(userId, PREFIX + action);
    }
    public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DENIED);
    }
}
