package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadSubmissionControllerPermissionTest {

    @Test
    void productCatalogAllowsAllLeadFormEntryPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = LeadSubmissionController.class.getMethod("getProductCatalog")
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:self-sourced:create', "
                + "'zsjos:lead:update', 'zsjos:lead:submitter-supplement')", authorization.value());
    }

}
