package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@SuppressWarnings("unchecked")
class ExamCollectionRosterTest {
    ExamCollectionRosterService service;
    ExamCollectionTaskMapper tasks;
    ExamCollectionMemberMapper members;
    ExamCollectionSubmissionMapper submissions;
    ExamCollectionTodoService todos;
    DeliveryClassMapper classes;
    ServiceRelationMapper relations;
    AdminUserApi users;
    StudentServiceArchiveStore archives;
    ExamCollectionTaskDO task;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); service = new ExamCollectionRosterService();
        tasks = inject("tasks", ExamCollectionTaskMapper.class); members = inject("members", ExamCollectionMemberMapper.class);
        submissions = inject("submissions", ExamCollectionSubmissionMapper.class); todos = inject("todos", ExamCollectionTodoService.class);
        classes = inject("classes", DeliveryClassMapper.class); relations = inject("relations", ServiceRelationMapper.class);
        users = inject("users", AdminUserApi.class); archives = inject("archives", StudentServiceArchiveStore.class);
        inject("objects", ExamCollectionPermissionProvider.class); inject("events", ExamCollectionEventMapper.class);
        var tokens = new ExamCollectionTokens(); ReflectionTestUtils.setField(tokens, "key", "test-key"); ReflectionTestUtils.setField(service, "tokens", tokens);
        task = new ExamCollectionTaskDO(); task.setId(1L); task.setTenantId(1L); task.setStatus(OPEN); task.setRevision(0);
        when(tasks.lock(1L)).thenReturn(task);
    }
    <T> T inject(String field, Class<T> type) { T value = mock(type); ReflectionTestUtils.setField(service, field, value); return value; }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void notificationFreezesClassPlannerAndIdentityWithoutReadingLaterClassChanges() {
        var clazz = new DeliveryClassDO().setId(4L).setClassName("测试班").setStatus("SERVING").setClassKind("NORMAL").setSystemClass(false).setHomeroomUserId(7L);
        clazz.setTenantId(1L); when(classes.selectByIdForUpdate(4L, 1L)).thenReturn(clazz);
        var planner = new AdminUserRespDTO(); planner.setId(7L); planner.setStatus(0); when(users.getUser(7L)).thenReturn(planner);
        var relation = new ServiceRelationDO(); relation.setId(10L); relation.setPersonId(20L); relation.setClassId(4L);
        when(relations.selectClassRelationsForUpdate(4L, 1L)).thenReturn(List.of(relation));
        var identity = new StudentInfoFormValueDO(); identity.setFieldKey("id_card"); identity.setValueText("syntheticX");
        when(archives.current(10L)).thenReturn(new StudentServiceArchiveStore.Snapshot(10L, 20L, 0, List.of(), List.of(identity), List.of()));
        var request = new Notify(); request.setRevision(0); request.setClassIds(List.of(4L)); service.notifyClasses(1L, request);
        var row = ArgumentCaptor.forClass(ExamCollectionMemberDO.class); verify(members).insert(row.capture());
        assertEquals(7L, row.getValue().getPlannerUserId()); assertEquals(10L, row.getValue().getServiceRelationId());
        assertEquals(64, row.getValue().getIdentityHash().length()); assertEquals("[4]", task.getClassIdsJson());
        assertEquals(1, task.getRevision()); verify(todos).synchronize(task, true);
        clazz.setHomeroomUserId(9L); assertEquals(7L, row.getValue().getPlannerUserId());
        request.setRevision(1); assertThrows(ServiceException.class, () -> service.notifyClasses(1L, request));
    }
    @Test void completeRequiresReasonWhenMissingAndReopenCreatesNewGeneration() {
        when(members.selectCount(any(LambdaQueryWrapperX.class))).thenReturn(2L);
        when(submissions.selectCount(any(LambdaQueryWrapperX.class))).thenReturn(1L);
        var request = new Transition(); request.setRevision(0);
        assertThrows(ServiceException.class, () -> service.complete(1L, request)); verifyNoInteractions(todos);
        request.setReason("本期收集结束"); service.complete(1L, request);
        assertEquals(COMPLETED, task.getStatus()); assertNotNull(task.getCompletedAt()); assertEquals(1, task.getRevision());
        request.setRevision(1); service.reopen(1L, request);
        assertEquals(OPEN, task.getStatus()); assertEquals(2, task.getRevision()); verify(todos, times(2)).synchronize(task, true);
    }
    @Test void staleVersionAndUnavailableClassDoNotCreateMembers() {
        var request = new Notify(); request.setRevision(4); request.setClassIds(List.of(4L));
        assertThrows(ServiceException.class, () -> service.notifyClasses(1L, request));
        request.setRevision(0); assertThrows(ServiceException.class, () -> service.notifyClasses(1L, request));
        verify(members, never()).insert(any(ExamCollectionMemberDO.class)); verifyNoInteractions(todos);
    }
}
