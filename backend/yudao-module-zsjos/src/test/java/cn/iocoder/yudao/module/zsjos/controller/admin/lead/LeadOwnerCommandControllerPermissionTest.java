package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadOwnerCommandControllerPermissionTest {

    @Test
    void ownerActionsUseDedicatedPermissions() throws NoSuchMethodException {
        String transferPermission = LeadOwnerCommandController.class
                .getMethod("transfer", Long.class,
                        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO.class)
                .getAnnotation(PreAuthorize.class).value();
        String releasePermission = LeadOwnerCommandController.class
                .getMethod("releasePublicSea", Long.class,
                        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadDispositionReqVO.class)
                .getAnnotation(PreAuthorize.class).value();

        assertTrue(transferPermission.contains("zsjos:lead:owner-transfer"));
        assertTrue(releasePermission.contains("zsjos:lead:owner-release-public-sea"));
    }
}
