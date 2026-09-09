package cn.iocoder.yudao.module.zsjos.controller.admin.order;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesOrderControllerTeamPermissionTest {

    @Test
    void teamEndpointsUseDedicatedPermission() {
        for (String name : new String[]{"getTeamPage", "searchTeamPage", "getTeamCursorPage",
                "searchTeamCursorPage", "getTeamStatusCounts"}) {
            Method method = Arrays.stream(SalesOrderController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertTrue(authorization.value().contains("zsjos:sales-order:query-team"), name);
        }
    }

    @Test
    void managementEndpointsUseManagementPermission() {
        for (String name : new String[]{"getManagementPage", "searchManagementPage", "getManagementCursor",
                "searchManagementCursor", "getManagementStatusCounts", "getManagementOrder"}) {
            Method method = Arrays.stream(SalesOrderController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertTrue(authorization.value().contains("zsjos:sales-order:query-management"), name);
        }
    }
}
