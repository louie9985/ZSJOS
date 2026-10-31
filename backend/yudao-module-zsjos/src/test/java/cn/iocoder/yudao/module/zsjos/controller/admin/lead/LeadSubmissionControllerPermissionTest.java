package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadSubmissionControllerPermissionTest {
    @Test
    void createLogsRemoveArchiveContactsUsingTheActualSanitizer() throws Exception {
        var filter = cn.iocoder.yudao.framework.apilog.core.filter.ApiAccessLogFilter.class;
        var sanitize = filter.getDeclaredMethod("sanitizeJson", String.class, String[].class);
        sanitize.setAccessible(true);
        for (String method : new String[]{"create", "createSelfSourced", "createEducationSelfSourced"}) {
            var annotation = LeadSubmissionController.class.getMethod(method, LeadCreateReqVO.class)
                    .getAnnotation(cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog.class);
            org.junit.jupiter.api.Assertions.assertNotNull(annotation);
            String result = (String) sanitize.invoke(null,
                    "{\"archiveQueryMobile\":\"synthetic-phone\",\"archiveQueryWechatId\":\"synthetic-wechat\",\"mobile\":\"synthetic-phone\",\"selectedPersonId\":7}",
                    annotation.sanitizeKeys());
            org.junit.jupiter.api.Assertions.assertFalse(result.contains("synthetic"));
            org.junit.jupiter.api.Assertions.assertTrue(result.contains("selectedPersonId"));
        }
    }

    @Test
    void archiveQueriesRequireTheirOwnEntryPermission() throws Exception {
        var request = cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadContactCheckReqVO.class;
        assertEquals("@ss.hasPermission('zsjos:lead:self-sourced:create')", LeadSubmissionController.class
                .getMethod("querySalesArchive", request).getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('zsjos:lead:education-self-sourced:create')", LeadSubmissionController.class
                .getMethod("queryEducationArchive", request).getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void contactCheckUsesConfiguredSubmissionPermissions() throws Exception {
        var auth = LeadSubmissionController.class.getMethod("checkContact",
                cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadContactCheckReqVO.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:self-sourced:create', 'zsjos:lead:education-self-sourced:create')", auth.value());
    }


    @Test
    void ordinaryCreateRequiresSubmitPermission() throws NoSuchMethodException {
        PreAuthorize authorization = LeadSubmissionController.class
                .getMethod("create", LeadCreateReqVO.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('zsjos:lead:submit')", authorization.value());
    }

    @Test
    void productCatalogAllowsAllLeadFormEntryPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = LeadSubmissionController.class.getMethod("getProductCatalog")
                .getAnnotation(PreAuthorize.class);

        // 产品目录是所有录单入口共用的下拉数据源，每新增一个录单入口都要把它的创建权限
        // 加进来，否则该入口的提交页拉不到目录、产品选不了。教务自拓新增
        // zsjos:lead:education-self-sourced:create。
        assertEquals("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:self-sourced:create', "
                + "'zsjos:lead:education-self-sourced:create', "
                + "'zsjos:lead:update', 'zsjos:lead:submitter-supplement')", authorization.value());
    }

    @Test
    void salesUserSimpleListRequiresSpecifyPermission() throws NoSuchMethodException {
        PreAuthorize authorization = LeadSalesUserController.class.getMethod("getSalesUserSimpleList")
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('zsjos:lead:submit:specify')", authorization.value());
    }

}
