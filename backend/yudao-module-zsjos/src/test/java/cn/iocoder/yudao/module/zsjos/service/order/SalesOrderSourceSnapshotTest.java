package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOrderSourceSnapshotTest {
    private SalesOrderRespVO.LeadProfileVO capture(String source, Boolean recorded, Long provider) {
        var service = new SalesOrderServiceImpl();
        var mapper = mock(LeadMapper.class);
        ReflectionTestUtils.setField(service, "leadMapper", mapper);
        var lead = new LeadDO();
        lead.setSourceUserId(10L);
        lead.setOwnerUserId(10L);
        var users = mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        when(users.getUserMap(any())).thenReturn(java.util.Map.of());
        ReflectionTestUtils.setField(service, "adminUserApi", users);
        lead.setSourceType(source);
        lead.setSourceProviderRecorded(recorded);
        lead.setSourceProviderUserId(provider);
        when(mapper.selectById(1L)).thenReturn(lead);
        var order = new SalesOrderDO();
        order.setLeadId(1L);
        return ReflectionTestUtils.invokeMethod(service, "buildLeadProfile", order);
    }

    @Test void capturesOnlyExplicitNoProviderEvidence() {
        assertEquals(true, capture("sales_self_sourced", true, null).getSelfSourcedWithoutProvider());
        assertEquals(true, capture("education_self_sourced", true, null).getSelfSourcedWithoutProvider());
        assertEquals(false, capture("sales_self_sourced", true, 2L).getSelfSourcedWithoutProvider());
        assertEquals(false, capture("internal_new_media", true, null).getSelfSourcedWithoutProvider());
        assertNull(capture("sales_self_sourced", null, null).getSelfSourcedWithoutProvider());
        assertNull(capture("sales_self_sourced", false, null).getSelfSourcedWithoutProvider());
    }

    @Test void preservesEvidenceThroughSnapshotSerializationWithoutInventingHistory() {
        var snapshot = new SalesOrderSnapshot();
        snapshot.setLeadProfile(capture("sales_self_sourced", true, null));
        var restored = SalesOrderSnapshot.read(JsonUtils.toJsonString(snapshot));
        assertEquals(true, restored.getLeadProfile().getSelfSourcedWithoutProvider());
        assertNull(restored.getLeadProfile().getSourceUserName());
        var historical = SalesOrderSnapshot.read("{\"leadProfile\":{\"sourceType\":\"sales_self_sourced\"}}");
        assertNull(historical.getLeadProfile().getSelfSourcedWithoutProvider());
    }
}
