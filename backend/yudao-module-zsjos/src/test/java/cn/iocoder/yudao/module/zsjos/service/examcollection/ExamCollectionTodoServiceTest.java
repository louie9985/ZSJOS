package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionMemberMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ExamCollectionTodoServiceTest {
    @InjectMocks private ExamCollectionTodoService service;
    @Mock private ExamCollectionMemberMapper members;
    @Mock private BusinessTaskCommandService commands;
    @Mock private ExamCollectionRecipientScope recipientScope;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper businessTasks;

    private ExamCollectionTaskDO task() {
        var task = new ExamCollectionTaskDO();
        task.setId(1L); task.setRevision(2); task.setStatus("OPEN"); task.setTitle("test");
        return task;
    }
    private ExamCollectionMemberDO member(long planner) {
        var member = new ExamCollectionMemberDO();
        member.setPlannerUserId(planner); member.setActive(true);
        member.setClassId(planner); member.setClassNameSnapshot("class");
        return member;
    }
    @Test void updatesEachPlannerByItsOwnIdempotentTaskId() {
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(member(10L), member(20L)));
        when(commands.create(any())).thenAnswer(call -> {
            var command = call.getArgument(0, cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand.class);
            return command.assigneeId() + 100L;
        });
        service.synchronize(task(), false);
        verify(commands).updatePendingById(eq(110L), eq(10L), anyString(), anyString(), isNull(), any());
        verify(commands).updatePendingById(eq(120L), eq(20L), anyString(), anyString(), isNull(), any());
        verify(commands, never()).updatePending(any(), any(), any(), any(), any(), any(), any());
        verify(commands, never()).cancel(any(), any(), any(), any(), any());
    }
    @Test void synchronizesManagerVisibleRosterAlongsidePlannerTodos() {
        var first = member(10L);
        var second = member(20L);
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(first, second));
        when(recipientScope.managers(any())).thenReturn(java.util.Map.of(30L, List.of(first)));
        service.synchronize(task(), false);
        verify(commands).create(argThat(c -> c.assigneeId().equals(30L)
                && c.summary().contains("0/1")));
        verify(commands).create(argThat(c -> c.assigneeId().equals(10L)));
        verify(commands).create(argThat(c -> c.assigneeId().equals(20L)));
    }
    @Test void managerWhoIsAlsoPlannerReceivesOneCombinedTodo() {
        var own = member(10L);
        var other = member(20L);
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(own, other));
        when(recipientScope.managers(any())).thenReturn(java.util.Map.of(10L, List.of(own, other)));
        service.synchronize(task(), false);
        verify(commands, times(1)).create(argThat(c -> c.assigneeId().equals(10L)
                && c.summary().contains("0/2")));
    }
    @Test void managerCompletesWhenVisibleMembersAreCollected() {
        var visible = member(10L); visible.setLastSubmissionId(99L);
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(visible, member(20L)));
        when(recipientScope.managers(any())).thenReturn(java.util.Map.of(30L, List.of(visible)));
        service.synchronize(task(), false);
        verify(commands).completeByKey(eq("exam-collection:1:2:30"), any());
        verify(commands, never()).create(argThat(c -> c.assigneeId().equals(30L)));
    }
    @Test void replacementCancelsAllOldAssigneesBeforeCreating() {
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(member(10L)));
        when(commands.create(any())).thenReturn(110L);
        service.synchronize(task(), true);
        var order = inOrder(commands);
        order.verify(commands).cancel(anyString(), eq(1L), isNull(), any(), anyString());
        order.verify(commands).create(any());
        order.verify(commands).updatePendingById(eq(110L), eq(10L), anyString(), anyString(), isNull(), any());
    }
    @Test void closingTaskCancelsEvenWhenRosterIsEmpty() {
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        var task = task(); task.setStatus("COMPLETED");
        service.synchronize(task, false);
        verify(commands).cancel(anyString(), eq(1L), isNull(), any(), anyString());
        verify(commands, never()).create(any());
    }
    @Test void collectionCompletionUsesOriginalGenerationInsteadOfCorruptedAssignee() {
        var member = member(10L); member.setLastSubmissionId(99L);
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(member));
        service.synchronize(task(), false);
        verify(commands).completeByKey(eq("exam-collection:1:2:10"), any());
        verify(commands, never()).complete(any(), any(), any(), any());
        verify(commands, never()).create(any());
    }
}
