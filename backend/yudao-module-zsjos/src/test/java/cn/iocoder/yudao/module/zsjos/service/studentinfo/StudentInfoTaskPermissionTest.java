package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoTaskMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StudentInfoTaskPermissionTest {
    @InjectMocks StudentInfoTaskPermissionProvider provider;
    @Mock StudentInfoTaskMapper tasks; @Mock PermissionApi permissions;
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void login(UserTypeEnum type){SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(20L).setUserType(type.getValue()),new MockHttpServletRequest());}
    @Test void anonymousAndPartnerCannotRead(){assertFalse(provider.canList(20L));login(UserTypeEnum.PARTNER);assertFalse(provider.hasPermission(1L,"read",20L));verifyNoInteractions(tasks,permissions);}
    @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper cases;
    @Mock cn.iocoder.yudao.module.zsjos.service.registration.RegistrationCaseObjectPermissionProvider casePermissions;

    @Test void readRequiresConfiguredPagePermission() {
        login(UserTypeEnum.ADMIN);
        assertFalse(provider.hasPermission(1L, "read", 20L));
        verifyNoInteractions(tasks);
    }

    @Test void rejectsAnotherOwnerAndUnknownActions() {
        login(UserTypeEnum.ADMIN);
        when(permissions.hasAnyPermissions(20L, "zsjos:registration:query-my")).thenReturn(true);
        var task = new StudentInfoTaskDO(); task.setOrderId(31L); task.setOrderItemId(42L);
        when(tasks.selectById(1L)).thenReturn(task);
        var registration = new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO();
        registration.setOrderId(31L);
        when(cases.selectByOrderItemId(42L)).thenReturn(registration);
        assertFalse(provider.hasPermission(1L, "read", 20L));
        when(casePermissions.isOwner(registration, 20L)).thenReturn(true);
        when(casePermissions.isOpen(registration)).thenReturn(true);
        assertTrue(provider.hasPermission(1L, "read", 20L));
        assertTrue(provider.hasPermission(1L, "reuse", 20L));
        assertFalse(provider.hasPermission(1L, "invented", 20L));
    }
}
