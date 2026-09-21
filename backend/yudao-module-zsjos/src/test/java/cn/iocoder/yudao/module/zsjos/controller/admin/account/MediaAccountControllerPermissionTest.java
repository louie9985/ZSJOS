package cn.iocoder.yudao.module.zsjos.controller.admin.account;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MediaAccountControllerPermissionTest {

    private static final String HISTORY_PERMISSION =
            "@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-account:maintenance')";

    @Test
    void historyEndpointAcceptsAccountQueryOrMaintenancePermission() throws Exception {
        assertEquals(HISTORY_PERMISSION, permission("maintenanceHistory"));
    }

    @Test
    void orderedStageTransitionRoutesAreNotExposed() {
        assertFalse(java.util.Arrays.stream(MediaAccountController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> java.util.Arrays.stream(mapping.value()))
                .anyMatch(path -> path.contains("advance-stage") || path.contains("rollback-stage")));
    }

    @Test
    void legacyStageHistoryRouteIsNotExposed() {
        assertFalse(java.util.Arrays.stream(MediaAccountController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> java.util.Arrays.stream(mapping.value()))
                .anyMatch(path -> path.contains("legacy-stage-history")));
    }

    private static String permission(String methodName) throws NoSuchMethodException {
        return MediaAccountController.class.getMethod(methodName, Long.class, PageParam.class)
                .getAnnotation(PreAuthorize.class).value();
    }
}
