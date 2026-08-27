package cn.iocoder.yudao.module.hrm.service.employee.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEvent;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEventType;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrmEmployeeLifecycleEventPublisherTest {

    @InjectMocks
    private HrmEmployeeLifecycleEventPublisher publisher;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private HrmEmployeeMapper employeeMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void publish_shouldIncludeTenantKeyAndEmployeeSnapshots() {
        TenantContextHolder.setTenantId(9L);
        HrmEmployeeDO before = employee(10L, 20L, 30L, "员工甲");
        HrmEmployeeDO after = employee(10L, 21L, 30L, "员工甲");
        HrmEmployeeDO leader = employee(30L, 31L, null, "负责人");
        when(employeeMapper.selectById(10L)).thenReturn(after);
        when(employeeMapper.selectById(30L)).thenReturn(leader);

        publisher.publish(HrmEmployeeLifecycleEventType.CHANGE_EFFECTIVE, 40L, before, 10L);

        ArgumentCaptor<HrmEmployeeLifecycleEvent> captor =
                ArgumentCaptor.forClass(HrmEmployeeLifecycleEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        HrmEmployeeLifecycleEvent event = captor.getValue();
        assertEquals(9L, event.getTenantId());
        assertEquals("hrm:change_effective:40:10", event.getEventKey());
        assertEquals(40L, event.getSourceId());
        assertEquals(20L, event.getBefore().getUserId());
        assertEquals(21L, event.getAfter().getUserId());
        assertEquals(31L, event.getBefore().getLeaderUserId());
        assertEquals(31L, event.getAfter().getLeaderUserId());
    }

    @Test
    void publish_shouldPropagateSynchronousListenerFailure() {
        TenantContextHolder.setTenantId(9L);
        HrmEmployeeDO employee = employee(10L, 20L, null, "员工甲");
        when(employeeMapper.selectById(10L)).thenReturn(employee);
        doThrow(new IllegalStateException("listener failed")).when(eventPublisher)
                .publishEvent(any(HrmEmployeeLifecycleEvent.class));

        assertThrows(IllegalStateException.class, () -> publisher.publish(
                HrmEmployeeLifecycleEventType.ENTRY_CONFIRMED, 10L, employee, 10L));
    }

    private HrmEmployeeDO employee(Long id, Long userId, Long leaderEmployeeId, String name) {
        HrmEmployeeDO employee = new HrmEmployeeDO();
        employee.setId(id);
        employee.setUserId(userId);
        employee.setLeaderEmployeeId(leaderEmployeeId);
        employee.setName(name);
        employee.setDeptId(100L);
        employee.setEntryStatus(1);
        employee.setStatus(1);
        return employee;
    }

}
