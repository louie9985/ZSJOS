package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubordinateSalesPauseAllPermissionTest {

    @Test
    void pauseAllUsesIndependentFeaturePermission() throws Exception {
        PreAuthorize authorization = SubordinateSalesController.class.getMethod("pauseAllDispatch")
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('zsjos:subordinate-sales:pause-all')", authorization.value());
    }
}
