package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

/** Media read scope never grants a command or an unrelated asset permission. */
public final class MediaStudentReadScope {
    public static final String QUERY_ALL = "zsjos:media-student:query-all";
    private MediaStudentReadScope() { }

    public static boolean canReadAll(PermissionApi permissionApi, Long userId) {
        return permissionApi.hasTenantReadAllAccess(userId)
                || permissionApi.hasAnyPermissions(userId, QUERY_ALL);
    }
}
