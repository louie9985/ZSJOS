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

    @Test
    void closeRequiresEditableRegistrationCase() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(10L);
        registrationCase.setStatus(RegistrationConstants.STATUS_PENDING);
        when(caseMapper.selectById(10L)).thenReturn(registrationCase);

        assertTrue(provider.hasPermission(10L, RegistrationConstants.COMMAND_CLOSE, 7L));

        registrationCase.setStatus(RegistrationConstants.STATUS_COMPLETED);
        assertFalse(provider.hasPermission(10L, RegistrationConstants.COMMAND_CLOSE, 7L));
    }
}
