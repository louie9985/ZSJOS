package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@SuppressWarnings("unchecked")
class ExamCollectionResolutionTest {
    ExamCollectionResolutionService service;
    ExamCollectionTaskMapper tasks;
    ExamCollectionSubmissionMapper submissions;
    ExamCollectionBindingMapper bindings;
    ExamCollectionEventMapper events;
    ServiceRelationMapper relations;
    StudentServiceArchiveStore archives;
    ExamCollectionMatchingService matching;
    ExamCollectionTaskDO task;
    ExamCollectionSubmissionDO row;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); service = new ExamCollectionResolutionService();
        tasks = inject("tasks", ExamCollectionTaskMapper.class); submissions = inject("submissions", ExamCollectionSubmissionMapper.class);
        bindings = inject("bindings", ExamCollectionBindingMapper.class); events = inject("events", ExamCollectionEventMapper.class);
        relations = inject("relations", ServiceRelationMapper.class); archives = inject("archives", StudentServiceArchiveStore.class);
        matching = inject("matching", ExamCollectionMatchingService.class); inject("objects", ExamCollectionPermissionProvider.class);
        task = new ExamCollectionTaskDO(); task.setId(1L); task.setTenantId(1L); task.setStatus(OPEN); when(tasks.lock(1L)).thenReturn(task);
        row = new ExamCollectionSubmissionDO(); row.setId(2L); row.setTaskId(1L); row.setTenantId(1L);
        row.setRevision(1); row.setStatus(EXCEPTION); row.setIdentityHash("synthetic"); when(submissions.lock(2L)).thenReturn(row);
        var target = new ServiceRelationDO(); target.setId(99L); target.setTenantId(1L); when(relations.selectByIdForUpdate(99L, 1L)).thenReturn(target);
        when(archives.current(99L)).thenReturn(new StudentServiceArchiveStore.Snapshot(99L, 5L, 3, List.of(), List.of(), List.of()));
    }
    <T> T inject(String field, Class<T> type) { T value = mock(type); ReflectionTestUtils.setField(service, field, value); return value; }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Resolve request() { var request = new Resolve(); request.setRevision(1); request.setArchiveRevision(3); request.setServiceRelationId(99L); request.setReason("人工确认归属"); return request; }
    @Test void manualOutsideRosterAssociationProcessesNewestFirstAndAudits() {
        var newer = new ExamCollectionSubmissionDO(); newer.setId(3L);
        when(submissions.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(newer, row));
        service.resolve(1L, 2L, request());
        var binding = ArgumentCaptor.forClass(ExamCollectionBindingDO.class); verify(bindings).insert(binding.capture());
        assertEquals(99L, binding.getValue().getServiceRelationId()); assertTrue(binding.getValue().getManual());
        var order = inOrder(matching); order.verify(matching).match(task, newer, null, "人工确认归属"); order.verify(matching).match(task, row, null, "人工确认归属");
        verify(events).insert(any(ExamCollectionEventDO.class));
    }
    @Test void changedArchiveOrSubmissionPreventsBindingAndWrites() {
        var request = request(); request.setArchiveRevision(2);
        assertThrows(ServiceException.class, () -> service.resolve(1L, 2L, request));
        request.setArchiveRevision(3); request.setRevision(0);
        assertThrows(ServiceException.class, () -> service.resolve(1L, 2L, request));
        verify(bindings, never()).insert(any(ExamCollectionBindingDO.class)); verifyNoInteractions(matching, events);
    }
    @Test void invalidationDoesNotDeleteMaterialsAndCannotInvalidateAnArchivedSubmission() {
        var request = new Invalidate(); request.setRevision(1); request.setReason("无效提交");
        service.invalidate(1L, 2L, request); assertEquals(INVALID, row.getStatus()); assertEquals(2, row.getRevision());
        verify(submissions, never()).deleteById(anyLong()); verifyNoInteractions(matching, archives, bindings);
        row.setStatus(ARCHIVED); request.setRevision(2);
        assertThrows(ServiceException.class, () -> service.invalidate(1L, 2L, request));
    }
    @Test void crossTaskSubmissionAndSilentReassignmentAreRejected() {
        row.setTaskId(8L); assertThrows(ServiceException.class, () -> service.resolve(1L, 2L, request()));
        row.setTaskId(1L); var binding = new ExamCollectionBindingDO(); binding.setServiceRelationId(88L);
        when(bindings.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(binding);
        assertThrows(ServiceException.class, () -> service.resolve(1L, 2L, request())); verifyNoInteractions(matching, events);
    }
}
