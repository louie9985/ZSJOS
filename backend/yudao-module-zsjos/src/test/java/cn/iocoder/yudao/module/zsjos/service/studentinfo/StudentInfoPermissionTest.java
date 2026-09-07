package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentInfoPermissionTest {
    @InjectMocks StudentInfoPermissionProvider provider;
    @Mock LeadMapper leads;
    @Mock LeadObjectPermissionService leadPermission;
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void login(UserTypeEnum type) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(20L).setUserType(type.getValue()),new MockHttpServletRequest());
    }
    @Test void anonymousAndPartnerCannotUseAdminPermissions() {
        assertFalse(provider.hasPermission(1L,"read",20L));
        login(UserTypeEnum.PARTNER); assertFalse(provider.hasPermission(1L,"create",20L));
        verifyNoInteractions(leads,leadPermission);
    }
    @Test void collaboratorCanReadButCannotGenerate() {
        login(UserTypeEnum.ADMIN);
        var lead=new LeadDO().setId(1L); when(leads.selectById(1L)).thenReturn(lead);
        when(leadPermission.canReadDetail(lead,20L)).thenReturn(true);
        assertTrue(provider.hasPermission(1L,"read",20L));
        assertFalse(provider.hasPermission(1L,"create",20L));
        assertFalse(provider.hasPermission(1L,"regenerate",20L));
    }
    @Test void unrelatedEmployeeDeniedAndUnknownActionFailsClosed() {
        login(UserTypeEnum.ADMIN);
        when(leads.selectById(1L)).thenReturn(new LeadDO().setId(1L));
        assertFalse(provider.hasPermission(1L,"read",20L));
        assertFalse(provider.hasPermission(1L,"invented",20L));
        assertFalse(provider.hasPermission(2L,"read",20L));
    }
}
