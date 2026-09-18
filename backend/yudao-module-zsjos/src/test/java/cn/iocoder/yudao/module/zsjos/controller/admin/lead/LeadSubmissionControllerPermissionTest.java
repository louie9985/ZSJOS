package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadSubmissionControllerPermissionTest {

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
