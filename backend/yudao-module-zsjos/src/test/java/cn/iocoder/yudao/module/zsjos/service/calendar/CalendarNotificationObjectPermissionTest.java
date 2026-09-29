package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyRecipientMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermissionAspect;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

class CalendarNotificationObjectPermissionTest {
    private CalendarNotifyBatchMapper batches;
    private CalendarNotifyRecipientMapper recipients;
    private PermissionApi grants;
    private CalendarNotifyBatchObjectPermissionProvider provider;
    private CalendarNotificationService proxy;
    private CalendarNotifyBatchDO batch;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(10L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
        batches = mock(CalendarNotifyBatchMapper.class); recipients = mock(CalendarNotifyRecipientMapper.class);
        grants = mock(PermissionApi.class);
        var access = new CalendarNotificationAccess(); ReflectionTestUtils.setField(access, "permissionApi", grants);
        provider = new CalendarNotifyBatchObjectPermissionProvider();
        ReflectionTestUtils.setField(provider, "mapper", batches); ReflectionTestUtils.setField(provider, "access", access);
        var target = new CalendarNotificationService();
        ReflectionTestUtils.setField(target, "batchMapper", batches);
        ReflectionTestUtils.setField(target, "recipientMapper", recipients);
        ReflectionTestUtils.setField(target, "notificationAccess", access);
        var factory = new AspectJProxyFactory(target); factory.addAspect(new ZsjosPermissionAspect(List.of(provider)));
        proxy = factory.getProxy();
        batch = new CalendarNotifyBatchDO().setId(7L).setCalendarType("COURSE").setCalendarId(100L).setEventType("DELETED");
        batch.setTenantId(10L); batch.setDeleted(false);
        when(batches.selectById(7L)).thenReturn(batch);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @Test void retainedHistoryRequiresOnlyActualTypeNotifyPermission() {
        when(grants.hasAnyPermissions(9L, CalendarNotificationAccess.COURSE_NOTIFY)).thenReturn(true);
        assertEquals("DELETED", proxy.getBatch(7L).getEventType());
        verify(grants, atLeastOnce()).hasAnyPermissions(9L, CalendarNotificationAccess.COURSE_NOTIFY);
        verify(grants, never()).hasAnyPermissions(9L, "zsjos:course-calendar:manage");
        verify(grants, never()).hasAnyPermissions(9L, CalendarNotificationAccess.COURSE_NOTIFY_ALL);
    }

    @Test void foreignAndLogicallyDeletedBatchesAreRejectedBeforeCheckingGrants() {
        batch.setTenantId(11L);
        code(CALENDAR_NOTIFY_BATCH_NOT_EXISTS.getCode(), () -> proxy.getBatch(7L));
        batch.setTenantId(10L); batch.setDeleted(true);
        code(CALENDAR_NOTIFY_BATCH_NOT_EXISTS.getCode(), () -> proxy.getBatch(7L));
        verifyNoInteractions(grants, recipients);
    }

    @Test void courseGrantDoesNotReadExamRecipientsAndAspectStopsBeforeQuery() {
        batch.setCalendarType("EXAM");
        when(grants.hasAnyPermissions(9L, CalendarNotificationAccess.COURSE_NOTIFY)).thenReturn(true);
        code(CALENDAR_NOTIFY_PERMISSION_DENIED.getCode(), () -> proxy.getRecipientPage(7L, new PageParam()));
        verifyNoInteractions(recipients);
        verify(batches, times(1)).selectById(7L);
        verify(grants).hasAnyPermissions(9L, CalendarNotificationAccess.EXAM_NOTIFY);
    }

    @Test void noActorCannotReadEvenExistingBatch() {
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
        code(CALENDAR_NOTIFY_PERMISSION_DENIED.getCode(), () -> proxy.getBatch(7L));
        verifyNoInteractions(batches, grants, recipients);
    }

    @Test void unknownActionNeverFallsBackToHistoryPermission() {
        assertFalse(provider.hasPermission(7L, "send", 9L));
        verifyNoInteractions(batches, grants, recipients);
    }

    private void code(int expected, Runnable action) {
        assertEquals(expected, assertThrows(ServiceException.class, action::run).getCode());
    }
}
