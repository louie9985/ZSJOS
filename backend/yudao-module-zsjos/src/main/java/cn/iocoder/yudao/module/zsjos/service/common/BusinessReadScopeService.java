package cn.iocoder.yudao.module.zsjos.service.common;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

/** Explicit personal query scope. The actor remains unchanged for projections and commands. */
@Service
public class BusinessReadScopeService {
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi userApi;

    /** Null means ALL only after administrator authorization. */
    public Long resolve(String scope, Long targetUserId, Long actorId) {
        if (actorId == null) throw exception(FORBIDDEN);
        if (scope == null || "SELF".equals(scope)) {
            if (targetUserId != null && !Objects.equals(targetUserId, actorId)) throw exception(BAD_REQUEST);
            return actorId;
        }
        if (!"ALL".equals(scope) && !"USER".equals(scope)) throw exception(BAD_REQUEST);
        if (!permissionApi.hasTenantReadAllAccess(actorId)) throw exception(FORBIDDEN);
        if ("ALL".equals(scope)) {
            if (targetUserId != null) throw exception(BAD_REQUEST);
            return null;
        }
        if (targetUserId == null || userApi.getUser(targetUserId) == null) throw exception(FORBIDDEN);
        return targetUserId;
    }
}
