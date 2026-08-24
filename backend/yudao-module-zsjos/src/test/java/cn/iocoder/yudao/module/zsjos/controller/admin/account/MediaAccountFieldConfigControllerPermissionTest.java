package cn.iocoder.yudao.module.zsjos.controller.admin.account;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaAccountFieldConfigControllerPermissionTest {
    @Test
    void publishedConfigIsReadableByAccountReadersOrMediaStudentUsers() throws Exception {
        Method method = MediaAccountFieldConfigController.class.getMethod("getPublished");
        assertEquals("@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-student:query-my')",
                method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());
    }
}
