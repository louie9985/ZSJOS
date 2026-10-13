package cn.iocoder.yudao.module.bpm.api.task;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BpmRunningBusinessInstanceTest {
    @Test void onlyExactActiveTenantAndBusinessInstanceRemainsReadable() {
        TenantContextHolder.setTenantId(1L);
        try {
            var service=mock(BpmProcessInstanceService.class);var instance=mock(ProcessInstance.class);
            var api=new BpmProcessInstanceApiImpl();ReflectionTestUtils.setField(api,"processInstanceService",service);
            when(service.getProcessInstance("p")).thenReturn(instance);when(instance.getTenantId()).thenReturn("1");when(instance.getBusinessKey()).thenReturn("batch:10");
            assertTrue(api.isRunning("p","batch:10"));assertFalse(api.isRunning("p","batch:11"));
            when(instance.isSuspended()).thenReturn(true);assertFalse(api.isRunning("p","batch:10"));
            when(instance.isSuspended()).thenReturn(false);TenantContextHolder.setTenantId(2L);assertFalse(api.isRunning("p","batch:10"));
            TenantContextHolder.setTenantId(1L);when(service.getProcessInstance("p")).thenReturn(null);assertFalse(api.isRunning("p","batch:10"));
        } finally {TenantContextHolder.clear();}
    }
}
