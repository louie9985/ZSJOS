package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionMemberMapper;
import cn.iocoder.yudao.module.zsjos.service.task.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@SuppressWarnings("unchecked")
class ExamCollectionTodoTest {
    private ExamCollectionTodoService todoService() {
        var service = new ExamCollectionTodoService();
        ReflectionTestUtils.setField(service, "recipientScope", mock(ExamCollectionRecipientScope.class));
        ReflectionTestUtils.setField(service, "businessTasks", mock(cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper.class));
        return service;
    }
    @Test void classRemovalCancelsOldPlannersAndReaddedCollectedRosterDoesNotCreateTodo() {
        var service = todoService();
        var members = mock(ExamCollectionMemberMapper.class);var commands = mock(BusinessTaskCommandService.class);
        ReflectionTestUtils.setField(service,"members",members);ReflectionTestUtils.setField(service,"commands",commands);
        var task = new ExamCollectionTaskDO();task.setId(1L);task.setStatus(OPEN);task.setRevision(4);task.setTitle("调整班级");
        var removed = new ExamCollectionMemberDO();removed.setPlannerUserId(7L);removed.setActive(false);
        var collected = new ExamCollectionMemberDO();collected.setPlannerUserId(8L);collected.setActive(true);collected.setLastSubmissionId(90L);
        var missing = new ExamCollectionMemberDO();missing.setPlannerUserId(9L);missing.setActive(true);missing.setClassId(6L);missing.setClassNameSnapshot("新增班");
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(removed,collected,missing));
        service.synchronize(task,true);
        verify(commands).cancel(eq(TASK_TYPE),eq(1L),isNull(),any(),anyString());
        verify(commands).completeByKey(eq("exam-collection:1:4:8"),any());
        var command = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);verify(commands).create(command.capture());
        assertEquals("exam-collection:1:4:9",command.getValue().idempotencyKey());
    }
    @Test void manyClassesKeepGenericSummaryWithinStorageLimitWithoutChangingSnapshots(){
        var service=todoService();var members=mock(ExamCollectionMemberMapper.class);var commands=mock(BusinessTaskCommandService.class);
        ReflectionTestUtils.setField(service,"members",members);ReflectionTestUtils.setField(service,"commands",commands);
        var task=new ExamCollectionTaskDO();task.setId(1L);task.setRevision(1);task.setStatus(OPEN);task.setTitle("合成任务");
        var rows=new java.util.ArrayList<ExamCollectionMemberDO>();
        for(int i=0;i<100;i++){var row=new ExamCollectionMemberDO();row.setId((long)i+1);row.setPlannerUserId(7L);row.setActive(true);row.setClassId((long)i+1);row.setClassNameSnapshot("长班级名称".repeat(30)+i);rows.add(row);}
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(rows);service.synchronize(task,true);
        var created=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);verify(commands).create(created.capture());
        assertTrue(created.getValue().summary().length()<1000);assertTrue(created.getValue().summary().contains("等 100 个班级"));
        assertEquals("长班级名称".repeat(30)+99,rows.get(99).getClassNameSnapshot());
    }
    @Test void plannerGenerationProgressCompletionAndReopenUseExistingCommands() {
        var service = todoService();
        var members = mock(ExamCollectionMemberMapper.class); var commands = mock(BusinessTaskCommandService.class);
        ReflectionTestUtils.setField(service, "members", members); ReflectionTestUtils.setField(service, "commands", commands);
        var task = new ExamCollectionTaskDO(); task.setId(1L); task.setStatus(OPEN); task.setTitle("考试资料"); task.setRevision(1);
        var one = new ExamCollectionMemberDO(); one.setPlannerUserId(7L); one.setActive(true); one.setClassId(4L); one.setClassNameSnapshot("测试班");
        var two = new ExamCollectionMemberDO(); two.setPlannerUserId(7L); two.setActive(true); two.setClassId(4L); two.setClassNameSnapshot("测试班");
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(one, two));
        service.synchronize(task, true);
        var created = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class); verify(commands).create(created.capture());
        assertEquals("exam-collection:1:1:7", created.getValue().idempotencyKey());
        assertTrue(created.getValue().summary().contains("0/2")); assertEquals(TASK_ACTION, created.getValue().actionCode());
        clearInvocations(commands); one.setLastSubmissionId(10L); service.synchronize(task, false);
        verify(commands).create(created.capture()); assertEquals("exam-collection:1:1:7", created.getValue().idempotencyKey());
        assertTrue(created.getValue().summary().contains("1/2"));
        clearInvocations(commands); two.setLastSubmissionId(11L); service.synchronize(task, false);
        verify(commands).completeByKey(eq("exam-collection:1:1:7"), any()); verify(commands, never()).create(any());
        clearInvocations(commands); task.setStatus(COMPLETED); service.synchronize(task, true);
        verify(commands).cancel(eq(TASK_TYPE), eq(1L), isNull(), any(), anyString()); verify(commands, never()).create(any());
        clearInvocations(commands); task.setStatus(OPEN); task.setRevision(3); two.setLastSubmissionId(null); service.synchronize(task, true);
        verify(commands).create(created.capture()); assertEquals("exam-collection:1:3:7", created.getValue().idempotencyKey());
    }
}
