package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadProviderDisplayTest {
    @Test
    void distinguishesPartnerAndRecordedNewMediaWithoutUsingNames() {
        LeadDO lead = new LeadDO();
        lead.setProviderOwnerId(1L);
        lead.setProviderOwnerType("partner");
        assertEquals("兼职", LeadProviderDisplay.identityLabel(lead));
        lead.setProviderOwnerType("system_user");
        lead.setSourceType("internal_new_media");
        assertEquals("新媒体提供方", LeadProviderDisplay.identityLabel(lead));
        lead.setSourceType("sales_self_sourced");
        assertEquals("员工（身份待核实）", LeadProviderDisplay.identityLabel(lead));
        lead.setSourceProviderRecorded(true);
        lead.setSourceProviderUserId(1L);
        assertEquals("新媒体提供方", LeadProviderDisplay.identityLabel(lead));
        lead.setSourceProviderUserId(2L);
        assertEquals("员工（身份待核实）", LeadProviderDisplay.identityLabel(lead));
    }

    @Test
    void onlyExplicitUnassistedSelfSourcingMeansSelf() {
        LeadDO lead = new LeadDO();
        lead.setSourceType("sales_self_sourced");
        assertEquals("提供方待核实", LeadProviderDisplay.identityLabel(lead));
        lead.setSourceProviderRecorded(false);
        assertEquals("提供方待核实", LeadProviderDisplay.identityLabel(lead));
        lead.setSourceProviderRecorded(true);
        assertEquals("销售本人（未关联新媒体）", LeadProviderDisplay.identityLabel(lead));
        lead.setSourceType("education_self_sourced");
        assertEquals("教务本人（未关联新媒体）", LeadProviderDisplay.identityLabel(lead));
        lead.setProviderOwnerId(1L);
        assertEquals("提供方待核实", LeadProviderDisplay.identityLabel(lead));
    }
}
