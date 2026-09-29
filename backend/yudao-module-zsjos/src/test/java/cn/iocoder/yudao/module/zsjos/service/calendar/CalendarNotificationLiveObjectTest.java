package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermissionAspect;
import cn.iocoder.yudao.module.zsjos.service.coursecalendar.CourseCalendarObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleObjectPermissionProvider;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarNotificationLiveObjectTest {
    private PermissionApi grants;
    private CourseCalendarEventDO course;
    private ExamScheduleDO exam;
    private CalendarNotificationObjectAccess proxy;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(10L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
        grants = mock(PermissionApi.class);
        var courses = mock(CourseCalendarEventMapper.class); var exams = mock(ExamScheduleMapper.class);
        course = new CourseCalendarEventDO().setId(7L); course.setTenantId(10L);
        exam = new ExamScheduleDO().setId(7L); exam.setTenantId(10L);
        when(courses.selectById(7L)).thenReturn(course); when(exams.selectById(7L)).thenReturn(exam);
        var access = new CalendarNotificationAccess(); ReflectionTestUtils.setField(access, "permissionApi", grants);
        var courseProvider = new CourseCalendarObjectPermissionProvider();
        var examProvider = new ExamScheduleObjectPermissionProvider();
        ReflectionTestUtils.setField(courseProvider, "mapper", courses); ReflectionTestUtils.setField(examProvider, "mapper", exams);
        for (var provider : List.of(courseProvider, examProvider)) {
            ReflectionTestUtils.setField(provider, "permissionApi", grants);
            ReflectionTestUtils.setField(provider, "notificationAccess", access);
        }
        var factory = new AspectJProxyFactory(new CalendarNotificationObjectAccess());
        factory.addAspect(new ZsjosPermissionAspect(List.of(courseProvider, examProvider))); proxy = factory.getProxy();
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @Test void notificationDoesNotRequireManagementOrAllPermission() {
        when(grants.hasAnyPermissions(9L, CalendarNotificationAccess.COURSE_NOTIFY)).thenReturn(true);
        when(grants.hasAnyPermissions(9L, CalendarNotificationAccess.EXAM_NOTIFY)).thenReturn(true);
        proxy.courseNotification(7L); proxy.examNotification(7L);
        assertThrows(ServiceException.class, () -> proxy.courseMaintenance(7L));
        assertThrows(ServiceException.class, () -> proxy.examMaintenance(7L));
        verify(grants, never()).hasAnyPermissions(9L, CalendarNotificationAccess.COURSE_NOTIFY_ALL);
        verify(grants, never()).hasAnyPermissions(9L, CalendarNotificationAccess.EXAM_NOTIFY_ALL);
    }
    @Test void managementDoesNotGrantNotification() {
        when(grants.hasAnyPermissions(9L, "zsjos:course-calendar:manage")).thenReturn(true);
        when(grants.hasAnyPermissions(9L, "zsjos:exam-calendar:manage")).thenReturn(true);
        proxy.courseMaintenance(7L); proxy.examMaintenance(7L);
        assertThrows(ServiceException.class, () -> proxy.courseNotification(7L));
        assertThrows(ServiceException.class, () -> proxy.examNotification(7L));
    }
    @Test void otherTenantAndDeletedObjectsAreRejectedBeforeGrants() {
        course.setTenantId(11L); exam.setTenantId(11L);
        assertThrows(ServiceException.class, () -> proxy.courseNotification(7L));
        assertThrows(ServiceException.class, () -> proxy.examNotification(7L));
        course.setTenantId(10L); exam.setTenantId(10L); course.setDeleted(true); exam.setDeleted(true);
        assertThrows(ServiceException.class, () -> proxy.courseMaintenance(7L));
        assertThrows(ServiceException.class, () -> proxy.examMaintenance(7L));
        verifyNoInteractions(grants);
    }
    @Test void noActorCannotUseEitherType() {
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
        assertThrows(ServiceException.class, () -> proxy.courseNotification(7L));
        assertThrows(ServiceException.class, () -> proxy.examMaintenance(7L));
        verifyNoInteractions(grants);
    }
}
