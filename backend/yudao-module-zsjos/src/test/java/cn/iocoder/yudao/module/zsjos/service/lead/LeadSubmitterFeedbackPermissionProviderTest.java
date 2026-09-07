package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.service.personnel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadSubmitterFeedbackPermissionProviderTest {
    @InjectMocks private LeadSubmitterFeedbackPermissionProvider provider;
    @Mock private LeadMapper leadMapper;
    @Mock private PartnerAccountService partnerAccountService;
    private LeadDO lead;
    @BeforeEach void setup() {
        lead = new LeadDO().setId(1L).setOwnerUserId(20L).setProviderOwnerType("system_user")
                .setProviderOwnerId(10L).setStatus("valid");
        when(leadMapper.selectById(1L)).thenReturn(lead);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void login(long id, UserTypeEnum type) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(id).setUserType(type.getValue()),
                new org.springframework.mock.web.MockHttpServletRequest());
    }
    @Test void employeeSubmitterCanReadButCannotSend() {
        login(10L, UserTypeEnum.ADMIN);
        assertTrue(provider.hasPermission(1L, "read", 10L));
        assertFalse(provider.hasPermission(1L, "create", 10L));
    }
    @Test void ownerCanSendAndUnrelatedEmployeeCannotRead() {
        login(20L, UserTypeEnum.ADMIN);
        assertTrue(provider.hasPermission(1L, "create", 20L));
        assertTrue(provider.hasPermission(1L, "read", 20L));
        login(30L, UserTypeEnum.ADMIN);
        assertFalse(provider.hasPermission(1L, "read", 30L));
        assertFalse(provider.hasPermission(1L, "create", 30L));
    }
    @Test void partnerCannotCollideWithAdminOwnerId() {
        login(20L, UserTypeEnum.PARTNER);
        when(partnerAccountService.requireContext(20L)).thenReturn(new PartnerContext(20L, 70L));
        assertFalse(provider.hasPermission(1L, "create", 20L));
        assertFalse(provider.hasPermission(1L, "read", 20L));
        lead.setProviderOwnerType("partner"); lead.setProviderOwnerId(70L);
        assertTrue(provider.hasPermission(1L, "read-partner", 20L));
        lead.setProviderOwnerId(71L);
        assertFalse(provider.hasPermission(1L, "read-partner", 20L));
    }
    @Test void anonymousAndMissingTenantScopedLeadFailClosed() {
        assertFalse(provider.hasPermission(1L, "read", 20L));
        login(20L, UserTypeEnum.ADMIN);
        when(leadMapper.selectById(1L)).thenReturn(null);
        assertFalse(provider.hasPermission(1L, "read", 20L));
    }
}
