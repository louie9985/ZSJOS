package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCreateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserOrganizationUpdateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerConvertReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

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
    @Mock private PermissionApi permissionApi;
    @Mock private RoleApi roleApi;

    @BeforeEach
    void setUpRole() {
        lenient().when(roleApi.getRoleByCode("part_time_partner"))
                .thenReturn(new RoleRespDTO().setId(99L).setCode("part_time_partner"));
    }

    @Test
    void createUsesAccountWithoutPostsAndBindsPartner() {
        when(adminUserApi.createUser(any())).thenReturn(88L);
        PartnerCreateReqVO request = new PartnerCreateReqVO();
        request.setPartnerNo("P001"); request.setName("Partner"); request.setMobile("13800138000");
        request.setUsername("partner_1"); request.setPassword("pass1234");

        service.create(request);

        ArgumentCaptor<AdminUserCreateReqDTO> accountCaptor = ArgumentCaptor.forClass(AdminUserCreateReqDTO.class);
        verify(adminUserApi).createUser(accountCaptor.capture());
        assertEquals(Set.of(), accountCaptor.getValue().getPostIds());
        verify(mapper).insert(argThat((PartnerDO partner) -> partner.getBoundSystemUserId().equals(88L)
                && PARTNER_STATUS_ENABLED.equals(partner.getStatus())));
        verify(permissionApi).addUserRole(88L, 99L);
    }

    @Test
    void disablePreservesPartnerAndDisablesAccount() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_ENABLED));

        service.disable(10L, stateRequest("inactive"));

        verify(mapper).updateById(argThat((PartnerDO partner) -> PARTNER_STATUS_DISABLED.equals(partner.getStatus())
                && partner.getDisabledAt() != null));
        verify(adminUserApi).updateUserStatus(88L, CommonStatusEnum.DISABLE.getStatus(), "inactive");
    }

    @Test
    void enableRejectsAccountThatAlreadyHasEmployeePosts() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_DISABLED));
        when(adminUserApi.getUser(88L)).thenReturn(new AdminUserRespDTO().setId(88L).setPostIds(Set.of(7L)));

        assertThrows(RuntimeException.class, () -> service.enable(10L, stateRequest("return")));

        verify(mapper, never()).updateById(any(PartnerDO.class));
        verify(adminUserApi, never()).updateUserStatus(anyLong(), anyInt(), anyString());
    }

    @Test
    void convertManagerSetsBothPostsAndMigratesHistoricalSnapshot() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_ENABLED));
        when(postApi.getPostByCode("new_media_operator")).thenReturn(post(101L));
        when(postApi.getPostByCode("dept_manager")).thenReturn(post(102L));
        PartnerConvertReqVO request = new PartnerConvertReqVO();
        request.setTargetType("new_media_manager"); request.setDeptId(20L);
        request.setMigrateHistoricalOrganization(true); request.setReason("convert");

        service.convert(10L, request);

        ArgumentCaptor<AdminUserOrganizationUpdateReqDTO> updateCaptor =
                ArgumentCaptor.forClass(AdminUserOrganizationUpdateReqDTO.class);
        verify(adminUserApi).updateUserOrganization(updateCaptor.capture());
        assertEquals(88L, updateCaptor.getValue().getUserId());
        assertEquals(20L, updateCaptor.getValue().getDeptId());
        assertEquals(Set.of(101L, 102L), updateCaptor.getValue().getPostIds());
        verify(mapper).updateById(argThat((PartnerDO partner) -> PARTNER_STATUS_CONVERTED.equals(partner.getStatus())));
        verify(leadMapper).updateSourceDeptByPartnerId(10L, 20L);
        verify(permissionApi).removeUserRole(88L, 99L);
    }

    @Test
    void convertEmployeeLeavesHistoricalSnapshotUntouchedWhenNotRequested() {
        when(mapper.selectById(10L)).thenReturn(partner(PARTNER_STATUS_ENABLED));
        when(postApi.getPostByCode("new_media_operator")).thenReturn(post(101L));
        PartnerConvertReqVO request = new PartnerConvertReqVO();
        request.setTargetType("new_media_employee"); request.setDeptId(20L);
        request.setMigrateHistoricalOrganization(false); request.setReason("convert");

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
