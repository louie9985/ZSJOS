package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateLeadActionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchLeadActionReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubordinateLeadActionPermissionTest {

    @Test
    void singleCommandsUseIndependentButtonPermissions() throws Exception {
        assertPermission("transferLead", "zsjos:subordinate-sales:lead-transfer");
        assertPermission("restoreLead", "zsjos:subordinate-sales:lead-restore");
        assertPermission("recycleLead", "zsjos:subordinate-sales:lead-recycle");
        assertPermission("releaseLeadToClaimPool", "zsjos:subordinate-sales:lead-release-claim-pool");
        assertPermission("releaseLeadToPublicSea", "zsjos:subordinate-sales:lead-release-public-sea");
    }

    @Test
    void batchCommandsResolveThePermissionFromTheConstrainedActionPath() throws Exception {
        PreAuthorize authorization = SubordinateSalesController.class
                .getMethod("batchLeadAction", String.class, SubordinateBatchLeadActionReqVO.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('zsjos:subordinate-sales:lead-' + #action)", authorization.value());
    }

    private static void assertPermission(String method, String permission) throws Exception {
        PreAuthorize authorization = SubordinateSalesController.class
                .getMethod(method, Long.class, SubordinateLeadActionReqVO.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('" + permission + "')", authorization.value());
    }
}
