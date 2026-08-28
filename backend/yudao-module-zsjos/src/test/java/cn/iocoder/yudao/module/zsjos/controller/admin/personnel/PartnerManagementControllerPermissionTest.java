package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartnerManagementControllerPermissionTest {

    private static final String MANAGE = "@ss.hasPermission('zsjos:partner:manage')";
    private static final String READ = "@ss.hasAnyPermissions('zsjos:partner:query', 'zsjos:partner:manage')";

    @Test
    void readEndpointsAcceptQueryOrManage() throws Exception {
        assertPermission("page", READ);
        assertPermission("leadPage", READ);
        assertPermission("lead", READ);
    }

    @Test
    void everyManagementEndpointRequiresConsolidatedManagePermission() throws Exception {
        for (String method : Set.of("create", "list", "disable", "enable", "convert", "updateMobile",
                "resetPassword", "assignmentCandidates", "updateAssignment", "assignmentLog")) {
            assertPermission(method, MANAGE);
        }
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = java.util.Arrays.stream(PartnerManagementController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertEquals(expected, method.getAnnotation(PreAuthorize.class).value());
    }
}
