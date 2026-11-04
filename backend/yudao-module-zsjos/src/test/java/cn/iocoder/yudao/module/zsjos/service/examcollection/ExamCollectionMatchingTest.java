package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@SuppressWarnings("unchecked")
class ExamCollectionMatchingTest {
    ExamCollectionMatchingService service;
    ExamCollectionMemberMapper members;
    ExamCollectionBindingMapper bindings;
    ExamCollectionSubmissionMapper submissions;
    ServiceRelationMapper relations;
    StudentServiceArchiveStore archives;
    ExamCollectionTaskDO task;
    ExamCollectionSubmissionDO submission;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); service = new ExamCollectionMatchingService();
        members = inject("members", ExamCollectionMemberMapper.class); bindings = inject("bindings", ExamCollectionBindingMapper.class);
        submissions = inject("submissions", ExamCollectionSubmissionMapper.class); relations = inject("relations", ServiceRelationMapper.class);
        archives = inject("archives", StudentServiceArchiveStore.class);
        inject("todos", ExamCollectionTodoService.class);
        task = new ExamCollectionTaskDO(); task.setId(1L); task.setTenantId(1L);
        submission = new ExamCollectionSubmissionDO(); submission.setId(2L); submission.setTaskId(1L); submission.setTenantId(1L);
        submission.setIdentityHash("synthetic-hash"); submission.setSequenceNo(2L);
        var relation = new ServiceRelationDO(); relation.setId(10L); relation.setTenantId(1L); relation.setDeleted(false);
        when(relations.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
    }
    <T> T inject(String field, Class<T> type) { T value = mock(type); ReflectionTestUtils.setField(service, field, value); return value; }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    ExamCollectionMemberDO member(long id) { var row = new ExamCollectionMemberDO(); row.setServiceRelationId(id); return row; }
    @Test void unmatchedAndAmbiguousAreRetainedWithoutArchiveMutation() {
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of());
        service.match(task, submission); assertEquals(EXCEPTION, submission.getStatus()); assertEquals("UNMATCHED", submission.getExceptionCode());
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(member(10), member(11)));
        service.match(task, submission); assertEquals("AMBIGUOUS", submission.getExceptionCode());
        verifyNoInteractions(archives);
        verify(bindings, never()).insert(any(ExamCollectionBindingDO.class));
    }
    @Test void uniqueRosterMatchAppendsArchiveAndMarksMemberCollected() {
        var member = member(10);
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(member));
        when(members.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(member);
        service.match(task, submission);
        verify(archives).apply(task, submission, 10L, null, "学员提交考试资料");
        assertEquals(ARCHIVED, submission.getStatus()); assertEquals(10L, submission.getServiceRelationId());
        assertEquals(2L, member.getLastSubmissionId()); verify(bindings).insert(any(ExamCollectionBindingDO.class));
    }
    @Test void manualOutsideRosterBindingReceivesRepeatedSubmissionAndOldSequenceDoesNotOverwrite() {
        var binding = new ExamCollectionBindingDO(); binding.setServiceRelationId(10L); binding.setManual(true);
        binding.setLastSequenceNo(1L); binding.setRevision(1);
        when(bindings.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(binding);
        service.match(task, submission);
        assertEquals(2L, binding.getLastSequenceNo()); assertEquals(2L, binding.getLastSubmissionId());
        verify(members, never()).selectList(any(LambdaQueryWrapperX.class));
        submission.setSequenceNo(1L); submission.setId(1L); service.match(task, submission);
        verify(archives, times(1)).apply(any(), any(), eq(10L), isNull(), anyString());
        assertEquals(2L, binding.getLastSubmissionId()); assertEquals(ARCHIVED, submission.getStatus());
    }
    @Test void invalidConfirmedBindingNeverFallsBackToAnotherCandidate() {
        var binding = new ExamCollectionBindingDO(); binding.setServiceRelationId(99L);
        when(bindings.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(binding);
        service.match(task, submission);
        assertEquals("INVALID_BINDING", submission.getExceptionCode());
        verifyNoInteractions(archives); verify(members, never()).selectList(any(LambdaQueryWrapperX.class));
    }
    @Test void closedTeachingServiceRetainsSubmissionAsExceptionWithoutArchiveWrite() {
        var relation=relations.selectByIdForUpdate(10L,1L);relation.setTeachingClosed(true);
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(member(10)));
        service.match(task,submission);assertEquals("SERVICE_CLOSED",submission.getExceptionCode());
        verifyNoInteractions(archives);verify(bindings,never()).insert(any(ExamCollectionBindingDO.class));
    }
}
