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
    @Test void plannerGenerationProgressCompletionAndReopenUseExistingCommands() {
        var service = new ExamCollectionTodoService();
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
        verify(commands).complete(eq(TASK_TYPE), eq(1L), eq(7L), any()); verify(commands, never()).create(any());
        clearInvocations(commands); task.setStatus(COMPLETED); service.synchronize(task, true);
        verify(commands).cancel(eq(TASK_TYPE), eq(1L), eq(7L), any(), anyString()); verify(commands, never()).create(any());
        clearInvocations(commands); task.setStatus(OPEN); task.setRevision(3); two.setLastSubmissionId(null); service.synchronize(task, true);
        verify(commands).create(created.capture()); assertEquals("exam-collection:1:3:7", created.getValue().idempotencyKey());
    }
}
