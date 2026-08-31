package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.order.SalesOrderController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SubordinateLeadReadPermissionContractTest {

    @Test
    void historyEndpointsUseDedicatedFeaturePermissions() throws Exception {
        assertPermission(LeadManagementController.class, "getLead", "zsjos:subordinate-sales:query", Long.class);
        assertPermission(LeadFollowUpController.class, "getPage", "zsjos:lead-detail:follow-up-read",
                Long.class, int.class, int.class);
        assertPermission(LeadAppealController.class, "getList", "zsjos:lead-detail:appeal-read", Long.class);
        assertPermission(LeadComplaintController.class, "leadList", "zsjos:lead-detail:complaint-read", Long.class);
        assertPermission(SalesOrderController.class, "getCustomerOrders", "zsjos:lead-detail:order-read", Long.class);
        assertPermission(SalesOrderController.class, "getCustomerOrder", "zsjos:lead-detail:order-read",
                Long.class, Long.class);
    }

    private static void assertPermission(Class<?> type, String method, String permission,
                                         Class<?>... parameterTypes) throws Exception {
        PreAuthorize authorization = type.getMethod(method, parameterTypes).getAnnotation(PreAuthorize.class);
        assertTrue(authorization.value().contains(permission),
                () -> type.getSimpleName() + "." + method + " must require " + permission);
    }
}
