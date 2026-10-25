package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadManagementControllerPermissionTest {

    @Test
    void getLeadAcceptsMediaStudentPagePermission() throws NoSuchMethodException {
        PreAuthorize annotation = LeadManagementController.class.getMethod("getLead", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains("'zsjos:media-student:query-my'"));
    }
    @Test
    void managementProfileAcceptsBothExistingPagePermissionsWithoutOpeningTheAllList() throws Exception {
        String metadata = LeadManagementController.class.getMethod("getManagementInboxFilterProfile")
                .getAnnotation(PreAuthorize.class).value();
        assertTrue(metadata.contains("'zsjos:lead:query'"));
        assertTrue(metadata.contains("'zsjos:subordinate-sales:query'"));
    }
}
