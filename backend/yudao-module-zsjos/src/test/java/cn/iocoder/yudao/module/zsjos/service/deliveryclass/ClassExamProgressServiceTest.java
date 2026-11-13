package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.ClassExamProgressVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class ClassExamProgressServiceTest {
    @InjectMocks ClassExamProgressService service;
    @Mock DeliveryClassMapper classes;
    @Mock ServiceRelationMapper relations;
    @Mock ClassExamProgressMapper progress;
    @Mock DictDataApi dictionaries;
    @Mock PermissionApi permissions;
    @Mock DeliveryClassObjectPermissionProvider objects;
    AutoCloseable mocks;
    ServiceRelationDO student;
    @BeforeEach void setup() {
        mocks = MockitoAnnotations.openMocks(this); TenantContextHolder.setTenantId(1L);
        when(classes.selectByIdForUpdate(10L,1L)).thenReturn(new DeliveryClassDO().setId(10L).setClassKind("NORMAL").setSystemClass(false));
        student = new ServiceRelationDO(); student.setId(20L); student.setDeliveryStage("supervision");
        student.setStudentNameSnapshot("测试学员");
        when(relations.selectClassRelationsForUpdate(10L,1L)).thenReturn(List.of(student));
        when(dictionaries.getDictDataList(anyString())).thenReturn(List.of());
        when(permissions.hasAnyPermissions(9L,ClassExamProgressService.PERMISSION)).thenReturn(true);
        when(objects.hasPermission(10L,"complete",9L)).thenReturn(true);
    }
    @AfterEach void cleanup() throws Exception { TenantContextHolder.clear(); mocks.close(); }
    @Test void unreadyStudentBlocksAllWrites() {
        student.setDeliveryStage("study_plan");
        var r=service.transition(10L,9L,new Command("SPRINT",0));
        assertFalse(r.changed()); assertEquals(20L,r.blockers().getFirst().serviceRelationId());
        verify(progress,never()).save(anyLong(),anyLong(),anyString());
        verify(progress,never()).saveMember(anyLong(),anyLong(),anyString());
    }
    @Test void sprintSynchronizesOnlyIndependentProgress() {
        assertTrue(service.transition(10L,9L,new Command("SPRINT",0)).changed());
        verify(progress).saveMember(20L,1L,"SPRINT"); verify(progress).save(10L,1L,"SPRINT");
        verify(relations).selectClassRelationsForUpdate(10L,1L); verifyNoMoreInteractions(relations);
    }
    @Test void cannotSkipSprint() { assertThrows(RuntimeException.class,()->service.transition(10L,9L,new Command("EXAM",0))); }
    @Test void staleVersionRejected() { assertThrows(RuntimeException.class,()->service.transition(10L,9L,new Command("SPRINT",3))); }
    @Test void emptyClassBlocked() {
        when(relations.selectClassRelationsForUpdate(10L,1L)).thenReturn(List.of());
        assertFalse(service.transition(10L,9L,new Command("SPRINT",0)).changed());
    }
    @Test void examRequiresAllSprint() {
        state("SPRINT",null); assertFalse(service.transition(10L,9L,new Command("EXAM",2)).changed());
    }
    @Test void examSynchronizesMembers() {
        state("SPRINT",new ClassExamProgressMapper.Member("SPRINT",null,null));
        assertTrue(service.transition(10L,9L,new Command("EXAM",2)).changed());
        verify(progress).saveMember(20L,1L,"EXAM");
    }
    @Test void examAloneCannotEnd() {
        state("EXAM",new ClassExamProgressMapper.Member("EXAM",null,null));
        assertFalse(service.transition(10L,9L,new Command("END",2)).changed());
    }
    @Test void nonGraduatedOutcomeAllowsEnd() {
        state("EXAM",new ClassExamProgressMapper.Member("EXAM","not_graduated","未结业"));
        assertTrue(service.transition(10L,9L,new Command("END",2)).changed());
        verify(progress,never()).saveMember(anyLong(),anyLong(),anyString());
    }
    @Test void outcomeBlocksClassRollback() {
        state("EXAM",new ClassExamProgressMapper.Member("EXAM","graduated","结业"));
        assertFalse(service.transition(10L,9L,new Command("BACK",2)).changed());
    }
    @Test void reopeningPreservesOutcomes() {
        state("ENDED",new ClassExamProgressMapper.Member("EXAM","graduated","结业"));
        assertTrue(service.transition(10L,9L,new Command("BACK",2)).changed());
        verify(progress,never()).saveMember(anyLong(),anyLong(),anyString());
    }
    @Test void endingBlocksStudentOutcomeRollback() {
        state("ENDED",new ClassExamProgressMapper.Member("EXAM","graduated","结业"));
        assertThrows(RuntimeException.class,()->service.outcome(10L,9L,new Outcome(20L,null,2)));
    }
    @Test void noPermissionCannotMutate() {
        when(permissions.hasAnyPermissions(9L,ClassExamProgressService.PERMISSION)).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.transition(10L,9L,new Command("SPRINT",0)));
    }
    @Test void oneUnreadyMemberBlocksReadyMemberToo() {
        var other = new ServiceRelationDO(); other.setId(21L); other.setDeliveryStage("study_plan");
        when(relations.selectClassRelationsForUpdate(10L,1L)).thenReturn(List.of(student,other));
        var result=service.transition(10L,9L,new Command("SPRINT",0));
        assertEquals(List.of(21L),result.blockers().stream().map(Student::serviceRelationId).toList());
        verify(progress,never()).saveMember(anyLong(),anyLong(),anyString());
    }
    @Test void outcomePersistsAuthoritativeLabelSnapshot() {
        state("EXAM",new ClassExamProgressMapper.Member("EXAM",null,null));
        var option=new cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO();
        option.setValue("graduated"); option.setLabel("结业"); option.setStatus(0);
        when(dictionaries.getDictDataList(anyString())).thenReturn(List.of(option));
        when(progress.outcome(20L,1L,"graduated","结业")).thenReturn(1);
        service.outcome(10L,9L,new Outcome(20L,"graduated",2));
        verify(progress).outcome(20L,1L,"graduated","结业");
    }
    @Test void unchangedHistoricalOutcomeDoesNotResolveNewLabel() {
        state("EXAM",new ClassExamProgressMapper.Member("EXAM","graduated","旧结业名称"));
        var result=service.outcome(10L,9L,new Outcome(20L,"graduated",2));
        assertEquals("旧结业名称",result.students().getFirst().outcomeLabel());
        verify(dictionaries,never()).validateDictDataList(anyString(),any());
        verify(progress,never()).outcome(anyLong(),anyLong(),any(),any());
    }
    @Test void outcomeCannotTargetAnotherClassMember() {
        state("EXAM",new ClassExamProgressMapper.Member("EXAM",null,null));
        assertThrows(RuntimeException.class,()->service.outcome(10L,9L,new Outcome(999L,"graduated",2)));
    }
    @Test void rollbackExamSynchronizesSprint() {
        state("EXAM",new ClassExamProgressMapper.Member("EXAM",null,null));
        assertTrue(service.transition(10L,9L,new Command("BACK",2)).changed());
        verify(progress).saveMember(20L,1L,"SPRINT");
    }
    @Test void rollbackSprintReturnsToStudy() {
        state("SPRINT",new ClassExamProgressMapper.Member("SPRINT",null,null));
        assertTrue(service.transition(10L,9L,new Command("BACK",2)).changed());
        verify(progress).saveMember(20L,1L,"STUDY");
    }
    private void state(String stage,ClassExamProgressMapper.Member member) {
        when(progress.get(10L,1L)).thenReturn(new ClassExamProgressMapper.Progress(stage,2));
        when(progress.member(20L,1L)).thenReturn(member);
    }
}
