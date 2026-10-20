package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceObjectPermissionProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StudentInfoPermissionTest {
 @InjectMocks StudentInfoPermissionProvider provider;
 @Mock RegistrationCaseMapper cases; @Mock ServiceRelationMapper services; @Mock StudentServiceObjectPermissionProvider servicePermission; @Mock PermissionApi permission;
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 void login(UserTypeEnum type){SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(20L).setUserType(type.getValue()),new MockHttpServletRequest());}
 RegistrationCaseDO task(){var c=new RegistrationCaseDO();c.setId(1L);c.setStatus("pending");return c;}
 @Test void anonymousAndPartnerDenied(){assertFalse(provider.hasPermission(1L,"read",20L));login(UserTypeEnum.PARTNER);assertFalse(provider.hasPermission(1L,"create",20L));verifyNoInteractions(cases);}
 @Test void poolReaderCanManageOnlyPendingTask(){login(UserTypeEnum.ADMIN);var c=task();when(cases.selectById(1L)).thenReturn(c);when(permission.hasAnyPermissions(eq(20L),any(String[].class))).thenReturn(true);assertTrue(provider.hasPermission(1L,"create",20L));c.setStatus("completed");assertFalse(provider.hasPermission(1L,"create",20L));assertTrue(provider.hasPermission(1L,"read",20L));assertFalse(provider.hasPermission(1L,"invented",20L));}
 @Test void serviceReaderCannotGenerateOrReadLink(){login(UserTypeEnum.ADMIN);when(cases.selectById(1L)).thenReturn(task());var relation=new ServiceRelationDO();relation.setId(8L);when(services.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(relation));when(servicePermission.hasPermission(8L,"read",20L)).thenReturn(true);assertTrue(provider.hasPermission(1L,"read",20L));assertFalse(provider.hasPermission(1L,"link-read",20L));assertFalse(provider.hasPermission(1L,"create",20L));}
}
