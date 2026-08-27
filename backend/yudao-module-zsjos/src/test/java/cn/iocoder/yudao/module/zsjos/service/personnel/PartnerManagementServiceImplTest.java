package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerManagementServiceImplTest {

    @InjectMocks private PartnerManagementServiceImpl service;
    @Mock private PartnerMapper mapper;
    @Mock private PartnerAccountService partnerAccountService;
    @Mock private PartnerOwnershipService ownershipService;

    @Test
    void createUsesIndependentPartnerAccount() {
        doAnswer(invocation -> { invocation.<PartnerDO>getArgument(0).setId(10L); return 1; })
                .when(mapper).insert(any(PartnerDO.class));
        PartnerCreateReqVO request = new PartnerCreateReqVO();
        request.setPartnerNo("P001"); request.setName("Partner"); request.setMobile("13800138000");
        request.setPassword("pass1234");

        service.create(request);

        verify(mapper).insert(argThat((PartnerDO partner) -> partner.getBoundSystemUserId() == null
                && PARTNER_STATUS_ENABLED.equals(partner.getStatus())));
        verify(partnerAccountService).create(eq(10L), eq("13800138000"), eq("pass1234"));
    }

    @Test
    void disablePreservesPartnerAndDisablesAccount() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_ENABLED));

        service.disable(10L, stateRequest("inactive"));

        verify(mapper).updateById(argThat((PartnerDO partner) -> PARTNER_STATUS_DISABLED.equals(partner.getStatus())
                && partner.getDisabledAt() != null));
        verify(partnerAccountService).setEnabled(10L, false);
    }

    @Test
    void enableRestoresPartnerAccount() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_DISABLED));

        service.enable(10L, stateRequest("return"));

        verify(mapper).updateById(argThat((PartnerDO value) -> PARTNER_STATUS_ENABLED.equals(value.getStatus())));
        verify(partnerAccountService).setEnabled(10L, true);
    }

    private PartnerDO partner(String status) {
        return new PartnerDO().setId(10L).setBoundSystemUserId(88L).setStatus(status);
    }

    private PartnerStateReqVO stateRequest(String reason) {
        PartnerStateReqVO request = new PartnerStateReqVO(); request.setReason(reason); return request;
    }

}
