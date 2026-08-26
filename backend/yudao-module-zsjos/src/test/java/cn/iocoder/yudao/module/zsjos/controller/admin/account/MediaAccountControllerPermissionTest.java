package cn.iocoder.yudao.module.zsjos.controller.admin.account;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaAccountControllerPermissionTest {

    private static final String HISTORY_PERMISSION =
            "@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-account:maintenance')";

    @Test
    void historyEndpointsAcceptAccountQueryOrMaintenancePermission() throws Exception {
        assertEquals(HISTORY_PERMISSION, permission("maintenanceHistory"));
        assertEquals(HISTORY_PERMISSION, permission("legacyStageHistory"));
    }

    private static String permission(String methodName) throws NoSuchMethodException {
        return MediaAccountController.class.getMethod(methodName, Long.class, PageParam.class)
                .getAnnotation(PreAuthorize.class).value();
    }
}
