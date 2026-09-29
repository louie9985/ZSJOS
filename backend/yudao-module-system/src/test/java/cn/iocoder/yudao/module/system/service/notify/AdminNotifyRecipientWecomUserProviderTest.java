package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminNotifyRecipientWecomUserProviderTest {
    @Test void unavailableEmployeePreferenceAndBindingHaveDistinctReasons() {
        var mapper = mock(AdminUserMapper.class);
        var provider = new AdminNotifyRecipientWecomUserProvider(); ReflectionTestUtils.setField(provider, "userMapper", mapper);
        assertNull(provider.getWecomUserId(7L)); assertEquals("EMPLOYEE_UNAVAILABLE", provider.getUnavailableReason(7L));
        var user = new AdminUserDO().setId(7L).setStatus(1).setWecomEnabled(true).setWecomUserId("synthetic");
        when(mapper.selectById(7L)).thenReturn(user);
        assertNull(provider.getWecomUserId(7L)); assertEquals("EMPLOYEE_UNAVAILABLE", provider.getUnavailableReason(7L));
        user.setStatus(0).setWecomEnabled(false);
        assertNull(provider.getWecomUserId(7L)); assertEquals("WECOM_PERSONAL_PUSH_DISABLED", provider.getUnavailableReason(7L));
        user.setWecomEnabled(true).setWecomUserId(" ");
        assertNull(provider.getWecomUserId(7L)); assertEquals("WECOM_BINDING_MISSING", provider.getUnavailableReason(7L));
        user.setWecomUserId(" synthetic "); assertEquals("synthetic", provider.getWecomUserId(7L));
        assertEquals("WECOM_RECIPIENT_UNAVAILABLE", provider.getUnavailableReason(7L));
    }
}
