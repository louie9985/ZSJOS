package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

/** Teaching-student visibility is independent of class and media-student visibility. */
public final class StudentReadScope {
    public static final String QUERY_ALL = "zsjos:student:query-all";

    private StudentReadScope() {}

    public static boolean canReadAll(PermissionApi permissions, Long userId) {
        return permissions.hasAnyPermissions(userId, QUERY_ALL);
    }
}
