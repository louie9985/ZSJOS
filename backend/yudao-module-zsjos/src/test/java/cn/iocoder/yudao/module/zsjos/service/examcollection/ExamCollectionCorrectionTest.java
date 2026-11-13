package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.Correction;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.Snapshot;

@SuppressWarnings("unchecked")
class ExamCollectionCorrectionTest {
    ExamCollectionCorrectionService service; ExamCollectionTaskMapper tasks; ExamCollectionSubmissionMapper submissions;
    ExamCollectionBindingMapper bindings; ExamCollectionMemberMapper members; ExamCollectionEventMapper events;
    ServiceRelationMapper relations; StudentServiceArchiveStore archives; ExamCollectionTodoService todos; ExamCollectionPermissionProvider objects;
    ExamCollectionTaskDO task; ExamCollectionSubmissionDO older, latest; ExamCollectionBindingDO binding;
    Snapshot source, target; ExamArchiveRestoration.Result restored;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); service=new ExamCollectionCorrectionService();
        tasks=inject("tasks",ExamCollectionTaskMapper.class);submissions=inject("submissions",ExamCollectionSubmissionMapper.class);
        bindings=inject("bindings",ExamCollectionBindingMapper.class);members=inject("members",ExamCollectionMemberMapper.class);
        events=inject("events",ExamCollectionEventMapper.class);relations=inject("relations",ServiceRelationMapper.class);
        archives=inject("archives",StudentServiceArchiveStore.class);todos=inject("todos",ExamCollectionTodoService.class);objects=inject("objects",ExamCollectionPermissionProvider.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"correction"),ExamCollectionMemberDO.class);
        task=new ExamCollectionTaskDO();task.setId(1L);task.setTenantId(1L);task.setRevision(4);task.setStatus(OPEN);when(tasks.lock(1L)).thenReturn(task);
        older=submission(2L,1L);latest=submission(3L,2L);when(submissions.lock(2L)).thenReturn(older);
        when(submissions.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(latest,older));
        binding=new ExamCollectionBindingDO();binding.setId(8L);binding.setTenantId(1L);binding.setTaskId(1L);binding.setRevision(2);
        binding.setServiceRelationId(20L);binding.setLastSubmissionId(3L);binding.setLastSequenceNo(2L);
        when(bindings.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(binding);
        for(long id:List.of(10L,20L)){var relation=new ServiceRelationDO();relation.setId(id);relation.setTenantId(1L);when(relations.selectByIdForUpdate(id,1L)).thenReturn(relation);}
        source=new Snapshot(20L,200L,5,List.of(),List.of(),List.of());target=new Snapshot(10L,100L,7,List.of(),List.of(),List.of());
        when(archives.current(20L)).thenReturn(source);when(archives.current(10L)).thenReturn(target);
        restored=new ExamArchiveRestoration.Result(source,Set.of("name"),Set.of("mobile"));
        when(archives.restoration(20L,1L,Set.of(2L,3L))).thenReturn(restored);
        when(archives.restore(eq(task),eq(Set.of(2L,3L)),eq(20L),eq(5),isNull(),anyString(),eq(8L),eq(3))).thenReturn(restored);
        when(archives.applyCorrection(eq(task),eq(latest),eq(10L),isNull(),anyString(),eq(8L),eq(3))).thenReturn(8);
    }
    <T>T inject(String name,Class<T> type){var mock=mock(type);ReflectionTestUtils.setField(service,name,mock);return mock;}
    ExamCollectionSubmissionDO submission(long id,long sequence){var row=new ExamCollectionSubmissionDO();row.setId(id);row.setTaskId(1L);row.setTenantId(1L);row.setStatus(ARCHIVED);row.setRevision(1);row.setIdentityHash("synthetic");row.setSubmittedAt(LocalDateTime.now());row.setSequenceNo(sequence);row.setServiceRelationId(20L);return row;}
    Correction request(){var r=new Correction();r.setRevision(1);r.setTaskRevision(4);r.setBindingRevision(2);r.setSourceArchiveRevision(5);r.setTargetArchiveRevision(7);r.setServiceRelationId(10L);r.setLatestSubmissionId(3L);r.setReason("确认错误归属");return r;}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void previewLocksServicesInOrderAndDoesNotMutate() {
        var result=service.preview(1L,2L,10L);assertEquals(2,result.getSubmissionCount());assertEquals(3L,result.getLatestSubmissionId());
        assertEquals(20L,result.getSourceArchive().getServiceRelationId());assertEquals(7,result.getTargetArchive().getRevision());
        var order=inOrder(relations);order.verify(relations).selectByIdForUpdate(10L,1L);order.verify(relations).selectByIdForUpdate(20L,1L);
        verifyNoInteractions(todos,events,members);verify(bindings,never()).updateById(any(ExamCollectionBindingDO.class));
    }
    @Test void movesWholeGroupRestoresSourceAndClearsCollectedMarkerExplicitly() {
        when(submissions.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(null,latest);
        service.correct(1L,2L,request());
        assertEquals(10L,older.getServiceRelationId());assertEquals(10L,latest.getServiceRelationId());assertEquals(2,older.getRevision());
        assertEquals(10L,binding.getServiceRelationId());assertTrue(binding.getManual());assertEquals(3,binding.getRevision());
        var order=inOrder(archives);order.verify(archives).restore(task,Set.of(2L,3L),20L,5,null,"确认错误归属",8L,3);
        order.verify(archives).applyCorrection(task,latest,10L,null,"确认错误归属",8L,3);
        var updates=ArgumentCaptor.forClass(LambdaUpdateWrapper.class);verify(members,times(2)).update(isNull(),updates.capture());
        var clear=updates.getAllValues().getFirst();String sql=clear.getSqlSet()+clear.getSqlSegment();
        assertTrue(sql.contains("last_submission_id"));assertTrue(sql.contains("tenant_id"));assertTrue(clear.getParamNameValuePairs().containsValue(null));
        assertEquals(5,task.getRevision());verify(todos).synchronize(task,true);
        var event=ArgumentCaptor.forClass(ExamCollectionEventDO.class);verify(events).insert(event.capture());assertEquals("CORRECT_BINDING",event.getValue().getAction());
        verify(members,never()).insert(any(ExamCollectionMemberDO.class));
    }
    @Test void anyStalePreviewVersionRejectsBeforeWrites() {
        var requests=new ArrayList<Correction>();
        var r=request();r.setRevision(0);requests.add(r);r=request();r.setTaskRevision(3);requests.add(r);
        r=request();r.setBindingRevision(1);requests.add(r);r=request();r.setSourceArchiveRevision(4);requests.add(r);
        r=request();r.setTargetArchiveRevision(6);requests.add(r);r=request();r.setLatestSubmissionId(2L);requests.add(r);
        for(var request:requests)assertThrows(ServiceException.class,()->service.correct(1L,2L,request));
        verify(archives,never()).restore(any(),anySet(),anyLong(),anyInt(),any(),anyString(),anyLong(),anyInt());
        verifyNoInteractions(members,todos,events);
    }
    @Test void rejectsCrossTenantSameTargetInvalidAndClosedServices() {
        older.setTenantId(2L);assertThrows(ServiceException.class,()->service.preview(1L,2L,10L));older.setTenantId(1L);
        assertThrows(ServiceException.class,()->service.preview(1L,2L,20L));
        older.setStatus(INVALID);assertThrows(ServiceException.class,()->service.preview(1L,2L,10L));older.setStatus(ARCHIVED);
        var closed=new ServiceRelationDO();closed.setTenantId(1L);closed.setTeachingClosed(true);when(relations.selectByIdForUpdate(20L,1L)).thenReturn(closed);
        assertThrows(ServiceException.class,()->service.preview(1L,2L,10L));verifyNoInteractions(events,todos);
    }
    @Test void completedTaskCanBeCorrectedWithoutReopeningCollection() {
        task.setStatus(COMPLETED);service.correct(1L,2L,request());assertEquals(COMPLETED,task.getStatus());verify(todos).synchronize(task,true);
    }
    @Test void permissionDeniedStopsBeforeReadingPersonalInformation() {
        doThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(DENIED)).when(objects).check(1L,"resolve",null);
        assertThrows(ServiceException.class,()->service.preview(1L,2L,10L));verifyNoInteractions(tasks,submissions,archives,relations);
    }
}
