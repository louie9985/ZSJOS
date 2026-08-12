package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadDuplicateReviewControllerPermissionTest {
    @Test
    void usesIndependentFeaturePermissions() throws Exception {
        assertEquals("@ss.hasPermission('zsjos:lead-duplicate-review:query')",
                LeadDuplicateReviewController.class.getMethod("page",
                        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewPageReqVO.class)
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('zsjos:lead-duplicate-review:process')",
                LeadDuplicateReviewController.class.getMethod("decide", Long.class,
                        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewDecisionReqVO.class)
                        .getAnnotation(PreAuthorize.class).value());
    }
}
