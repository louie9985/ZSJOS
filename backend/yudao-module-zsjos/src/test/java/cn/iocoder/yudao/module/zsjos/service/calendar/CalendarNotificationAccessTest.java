package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationAccessTest {
    @InjectMocks private CalendarNotificationAccess access;
    @Mock private PermissionApi permissions;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(10L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @ParameterizedTest
    @CsvSource({"EXAM,false,false", "EXAM,false,true", "EXAM,true,false", "EXAM,true,true",
            "COURSE,false,false", "COURSE,false,true", "COURSE,true,false", "COURSE,true,true"})
    void allRequiresBothGrantsAndSpecifiedOnlyNeedsNotify(String kind, boolean notify, boolean all) {
        String prefix = "EXAM".equals(kind) ? "zsjos:exam-calendar:" : "zsjos:course-calendar:";
        when(permissions.hasAnyPermissions(7L, prefix + "notify")).thenReturn(notify);
        if (notify) when(permissions.hasAnyPermissions(7L, prefix + "notify-all")).thenReturn(all);
        if (notify) assertTrue(access.check(kind, false));
        else assertEquals(CALENDAR_NOTIFY_PERMISSION_DENIED.getCode(),
                assertThrows(ServiceException.class, () -> access.check(kind, false)).getCode());
        if (notify && all) assertTrue(access.check(kind, true));
        else assertEquals((notify ? CALENDAR_NOTIFY_ALL_PERMISSION_DENIED : CALENDAR_NOTIFY_PERMISSION_DENIED).getCode(),
                assertThrows(ServiceException.class, () -> access.check(kind, true)).getCode());
        verify(permissions, never()).hasAnyPermissions(7L, prefix + "manage");
    }

    @Test void wrongCalendarAndLegacyOrManageGrantsCannotAuthorize() {
        when(permissions.hasAnyPermissions(7L, CalendarNotificationAccess.COURSE_NOTIFY)).thenReturn(false);
        assertEquals(CALENDAR_NOTIFY_PERMISSION_DENIED.getCode(),
                assertThrows(ServiceException.class, () -> access.check("COURSE", false)).getCode());
        verify(permissions).hasAnyPermissions(7L, CalendarNotificationAccess.COURSE_NOTIFY);
        verifyNoMoreInteractions(permissions);
    }

    @Test void unknownKindDoesNotSelectCoursePermission() {
        assertEquals(CALENDAR_NOTIFY_TYPE_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> access.check("OTHER", false)).getCode());
        verifyNoInteractions(permissions);
    }
}
