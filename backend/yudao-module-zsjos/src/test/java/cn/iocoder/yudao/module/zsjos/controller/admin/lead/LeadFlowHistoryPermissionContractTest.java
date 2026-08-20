package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadFlowHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadFlowHistoryPermissionContractTest {

    @Test
    void endpointRequiresFeaturePermissionAndServiceRequiresLeadObjectRead() throws Exception {
        PreAuthorize feature = LeadManagementController.class.getMethod("getFlowHistory", Long.class)
                .getAnnotation(PreAuthorize.class);
        ZsjosPermission object = LeadFlowHistoryService.class.getMethod("getHistory", Long.class)
                .getAnnotation(ZsjosPermission.class);

        assertEquals("@ss.hasPermission('zsjos:lead-detail:flow-read')", feature.value());
        assertEquals("lead", object.bizType());
        assertEquals("#leadId", object.bizId());
        assertEquals("flow-read", object.action());
    }
}
