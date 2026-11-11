package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@SuppressWarnings("unchecked")
class ExamCollectionReminderTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void popupAcknowledgementIsOwnedIdempotentAndNeverCompletesCollection() {
        TenantContextHolder.setTenantId(1L);
        var service = new ExamCollectionReminderService();
        var recipientScope = new ExamCollectionRecipientScope();
        ReflectionTestUtils.setField(recipientScope, "permissions", mock(PermissionApi.class));
        ReflectionTestUtils.setField(service, "recipientScope", recipientScope);
        var permissions = mock(PermissionApi.class); var business = mock(BusinessTaskMapper.class);
        var tasks = mock(ExamCollectionTaskMapper.class); var members = mock(ExamCollectionMemberMapper.class);
        var events = mock(ExamCollectionEventMapper.class); var links = mock(ExamCollectionTaskService.class);
        ReflectionTestUtils.setField(service, "permissions", permissions); ReflectionTestUtils.setField(service, "businessTasks", business);
        ReflectionTestUtils.setField(service, "tasks", tasks); ReflectionTestUtils.setField(service, "members", members);
        ReflectionTestUtils.setField(service, "events", events); ReflectionTestUtils.setField(service, "taskService", links);
        var task = new ExamCollectionTaskDO(); task.setId(1L); task.setTenantId(1L); task.setStatus(OPEN); task.setTitle("测试收集");
        when(tasks.selectById(1L)).thenReturn(task);
        var todo = new BusinessTaskDO(); todo.setId(9L); todo.setBizId(1L); todo.setBizType(BIZ_TYPE); todo.setStatus("pending"); todo.setTaskType(TASK_TYPE); todo.setAssigneeId(7L); todo.setTenantId(1L);
        when(business.selectMyPendingByBusiness(7L, BIZ_TYPE, null)).thenReturn(List.of(todo));
        var member = new ExamCollectionMemberDO(); member.setClassNameSnapshot("测试班");
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(member));
        when(events.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of());
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class, service::pending);
            when(permissions.hasAnyPermissions(7L, "zsjos:business-task:query")).thenReturn(true);
            assertEquals(List.of(9L),service.state().unreadTodoIds());
            assertEquals(1, service.reminders().size());
            assertThrows(ServiceException.class, () -> service.acknowledge(List.of(9L, 99L)));
            verify(events, never()).insert(any(ExamCollectionEventDO.class));
            service.acknowledge(List.of(9L, 9L)); verify(events, times(1)).insert(any(ExamCollectionEventDO.class));
            var seen = new ExamCollectionEventDO(); seen.setDetailsJson(JsonUtils.toJsonString(new ExamCollectionReminderService.Seen(9L)));
            when(events.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(seen));
            assertTrue(service.reminders().isEmpty()); assertEquals(1, service.pending().size());
            assertTrue(service.state().unreadTodoIds().isEmpty());assertEquals(1,service.state().pending().size());
            service.acknowledge(List.of(9L)); verify(events, times(1)).insert(any(ExamCollectionEventDO.class));
            verify(tasks, never()).updateById(any(ExamCollectionTaskDO.class));
            verify(business, never()).updateById(any(BusinessTaskDO.class));
            todo.setAssigneeId(8L); assertTrue(service.pending().isEmpty());
        }
    }
}
