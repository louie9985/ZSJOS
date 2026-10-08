package cn.iocoder.yudao.module.bpm.convert.task;

import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BpmTaskNotificationSubjectTest {
    @Test void supportsTypedAndLegacyAdminAndPartnerWithoutFabricatingAdminIdentity() {
        for (String id : new String[] {"20", "2:20", "3:20", "invalid", ""}) {
            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getStartUserId()).thenReturn(id);
            when(instance.getProcessVariables()).thenReturn(Map.of("externalStartUserName", "合作方历史姓名"));
            Task task = mock(Task.class); when(task.getAssignee()).thenReturn("10");
            var result = BpmTaskConvert.INSTANCE.convert(instance, new AdminUserRespDTO().setId(20L).setNickname("管理员"), task);
            assertEquals(10L, result.getAssigneeUserId());
            if (id.equals("3:20")) { assertEquals(3, result.getStartSubject().getUserType()); assertEquals("合作方历史姓名", result.getStartUserNickname()); }
            else if (id.equals("20") || id.equals("2:20")) assertEquals("管理员", result.getStartUserNickname());
            else { assertNull(result.getStartSubject()); assertEquals("发起人信息不可用", result.getStartUserNickname()); }
        }
    }
    @Test void unavailableAdminOrPartnerNameDoesNotPreventTaskNotificationConversion() {
        for (String id : new String[] {"20", "3:20", "invalid"}) {
            ProcessInstance instance = mock(ProcessInstance.class); when(instance.getStartUserId()).thenReturn(id);
            when(instance.getProcessVariables()).thenReturn(Map.of());
            Task task = mock(Task.class); when(task.getAssignee()).thenReturn("10");
            var result = BpmTaskConvert.INSTANCE.convert(instance, null, task);
            assertEquals("发起人信息不可用", result.getStartUserNickname()); assertEquals(10L, result.getAssigneeUserId());
        }
    }
}
