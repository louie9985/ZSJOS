package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.order.SalesOrderController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SubordinateLeadReadPermissionContractTest {

    @Test
    void subordinateQueryPermissionIsAcceptedByEveryReadEndpoint() throws Exception {
        assertSubordinatePermission(LeadManagementController.class, "getLead", Long.class);
        assertSubordinatePermission(LeadFollowUpController.class, "getPage", Long.class, int.class, int.class);
        assertSubordinatePermission(LeadAppealController.class, "getList", Long.class);
        assertSubordinatePermission(LeadComplaintController.class, "leadList", Long.class);
        assertSubordinatePermission(SalesOrderController.class, "getCustomerOrders", Long.class);
        assertSubordinatePermission(SalesOrderController.class, "getCustomerOrder", Long.class, Long.class);
    }

    private static void assertSubordinatePermission(Class<?> type, String method, Class<?>... parameterTypes)
            throws Exception {
        PreAuthorize authorization = type.getMethod(method, parameterTypes).getAnnotation(PreAuthorize.class);
        assertTrue(authorization.value().contains("zsjos:subordinate-sales:query"),
                () -> type.getSimpleName() + "." + method + " must accept subordinate-sales query permission");
    }
}
