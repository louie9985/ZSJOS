package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserPartnerConversionReqDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerConvertReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
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
    @Mock private AdminUserApi adminUserApi;
    @Mock private PostApi postApi;
    @Mock private LeadMapper leadMapper;
    @Mock private PartnerAccountService partnerAccountService;

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
        verifyNoInteractions(adminUserApi);
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

    @Test
    void convertManagerSetsBothPostsAndMigratesHistoricalSnapshot() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_ENABLED));
        when(postApi.getPostByCode("new_media_operator")).thenReturn(post(101L));
        when(postApi.getPostByCode("dept_manager")).thenReturn(post(102L));
        PartnerConvertReqVO request = new PartnerConvertReqVO();
        request.setTargetType("new_media_manager"); request.setDeptId(20L);
        request.setUsername("employee_1"); request.setPassword("pass1234");
        request.setMigrateHistoricalOrganization(true); request.setReason("convert");
        when(adminUserApi.convertPartnerToEmployee(any())).thenReturn(88L);

        service.convert(10L, request);

        verify(adminUserApi).convertPartnerToEmployee(argThat((AdminUserPartnerConversionReqDTO value) ->
                value.getExistingUserId().equals(88L) && value.getDeptId().equals(20L)
                        && value.getPostIds().containsAll(java.util.Set.of(101L, 102L))));
        verify(partnerAccountService).setEnabled(10L, false);
        verify(mapper).updateById(argThat((PartnerDO partner) -> PARTNER_STATUS_CONVERTED.equals(partner.getStatus())));
        verify(leadMapper).updateSourceDeptByPartnerId(10L, 20L);
    }

    @Test
    void convertEmployeeLeavesHistoricalSnapshotUntouchedWhenNotRequested() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_ENABLED));
        when(postApi.getPostByCode("new_media_operator")).thenReturn(post(101L));
        PartnerConvertReqVO request = new PartnerConvertReqVO();
        request.setTargetType("new_media_employee"); request.setDeptId(20L);
        request.setUsername("employee_1"); request.setPassword("pass1234");
        request.setMigrateHistoricalOrganization(false); request.setReason("convert");
        when(adminUserApi.convertPartnerToEmployee(any())).thenReturn(88L);

        service.convert(10L, request);

        verify(leadMapper, never()).updateSourceDeptByPartnerId(anyLong(), anyLong());
    }

    private PartnerDO partner(String status) {
        return new PartnerDO().setId(10L).setBoundSystemUserId(88L).setStatus(status);
    }

    private PartnerStateReqVO stateRequest(String reason) {
        PartnerStateReqVO request = new PartnerStateReqVO(); request.setReason(reason); return request;
    }

    private PostRespDTO post(Long id) {
        return new PostRespDTO().setId(id).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }
}
