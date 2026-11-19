package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationCaseObjectPermissionProviderTest {

    @InjectMocks private RegistrationCaseObjectPermissionProvider provider;
    @Mock private RegistrationCaseMapper caseMapper;
    @Mock private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    @Mock private cn.iocoder.yudao.module.system.api.user.AdminUserApi users;

    @Test
    void permitsTenantRecoveryOfDisabledOwnerWithDedicatedPermission() {
        var registrationCase = new RegistrationCaseDO();
        registrationCase.setStatus(RegistrationConstants.STATUS_PENDING);
        registrationCase.setOwnerUserId(8L);
        when(caseMapper.selectById(10L)).thenReturn(registrationCase);
        when(permissions.hasAnyPermissions(7L, "zsjos:registration:transfer")).thenReturn(true);
        when(permissions.hasTenantReadAllAccess(7L)).thenReturn(true);
        when(users.getUser(8L)).thenReturn(new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO()
                .setId(8L).setStatus(1));
        assertTrue(provider.hasPermission(10L, "transfer", 7L));
        assertFalse(provider.hasPermission(10L, "update", 7L));
    }

    @Test
    void closeRequiresEditableRegistrationCase() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(10L);
        registrationCase.setOwnerUserId(7L);
        registrationCase.setStatus(RegistrationConstants.STATUS_PENDING);
        when(caseMapper.selectById(10L)).thenReturn(registrationCase);

        assertTrue(provider.hasPermission(10L, RegistrationConstants.COMMAND_CLOSE, 7L));
        assertFalse(provider.hasPermission(10L, RegistrationConstants.COMMAND_CLOSE, 9L));
        assertFalse(provider.hasPermission(10L, "read", 9L));

        registrationCase.setStatus(RegistrationConstants.STATUS_COMPLETED);
        assertFalse(provider.hasPermission(10L, RegistrationConstants.COMMAND_CLOSE, 7L));
    }
}
