package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo.HrmLifecycleReminderConfigVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment.HrmEmployeeChangeRecordMapper;
import cn.iocoder.yudao.module.hrm.service.config.HrmConfigService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class HrmLifecycleReminderServiceTest {
    HrmLifecycleReminderService service;
    HrmConfigService config;
    HrmEmployeeMapper employees;
    HrmEmployeeChangeRecordMapper records;
    AdminUserApi users;
    NotifyBusinessEventApi notify;
    HrmLifecycleReminderConfigVO settings;
    HrmEmployeeDO employee;
    @BeforeEach void setup() {
        service=new HrmLifecycleReminderService(); config=mock(HrmConfigService.class);
        employees=mock(HrmEmployeeMapper.class); records=mock(HrmEmployeeChangeRecordMapper.class);
        users=mock(AdminUserApi.class); notify=mock(NotifyBusinessEventApi.class);
        ReflectionTestUtils.setField(service,"config",config); ReflectionTestUtils.setField(service,"employees",employees);
        ReflectionTestUtils.setField(service,"records",records); ReflectionTestUtils.setField(service,"users",users);
        ReflectionTestUtils.setField(service,"notifications",notify); ReflectionTestUtils.setField(service,"departments",mock(DeptApi.class));
        settings=new HrmLifecycleReminderConfigVO(); var r=settings.getRegular();
        r.setEnabled(true); r.setAdvanceDays(0); r.setTriggerTime("00:00"); r.setDeptIds(List.of(10L)); r.setRecipientUserIds(List.of(7L));
        r.setEnabledSince(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(2));
        when(config.getConfigValueList(7)).thenAnswer(i->List.of(JsonUtils.toJsonString(settings)));
        employee=new HrmEmployeeDO().setId(1L).setName("合成试用员工").setDeptId(10L).setStatus(2).setEntryStatus(1)
                .setPlannedRegularTime(LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay());
        when(employees.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(employee));
        when(users.getUserList(anyCollection())).thenReturn(List.of(new AdminUserRespDTO().setId(7L).setStatus(0)));
        when(notify.publishDurable(any())).thenReturn(1);
    }
    @Test void dueWithZeroAdvanceHasOneStableEventAndNoEmployeeWrites() {
        service.dispatch(); service.dispatch();
        var captor=ArgumentCaptor.forClass(NotifyBusinessEvent.class); verify(notify,times(2)).publishDurable(captor.capture());
        assertEquals(captor.getAllValues().get(0).getSourceEventKey(),captor.getAllValues().get(1).getSourceEventKey());
        assertEquals("due",captor.getValue().getPayload().get("phase"));
        assertEquals("FIXED",captor.getValue().getRecipientMode());
        assertEquals(7L,captor.getValue().validatedFixedRecipients().getFirst().getUserId());
        assertTrue(mockingDetails(employees).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("selectList")));
        assertTrue(service.isCurrent(captor.getValue()));
        employee.setStatus(1); assertFalse(service.isCurrent(captor.getValue()));
    }
    @Test void advanceAndDueUseDistinctPhaseKeys() {
        settings.getRegular().setAdvanceDays(1); employee.setPlannedRegularTime(employee.getPlannedRegularTime().plusDays(1));
        service.dispatch(); var captor=ArgumentCaptor.forClass(NotifyBusinessEvent.class); verify(notify).publishDurable(captor.capture());
        assertEquals("advance",captor.getValue().getPayload().get("phase"));
    }
    @Test void oldDatesDisabledRulesAndInactiveRecipientsDoNotSend() {
        employee.setPlannedRegularTime(employee.getPlannedRegularTime().minusDays(10)); service.dispatch(); verifyNoInteractions(notify);
        settings.getRegular().setEnabled(false); service.dispatch(); verifyNoInteractions(notify);
        settings.getRegular().setEnabled(true); employee.setPlannedRegularTime(LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay());
        when(users.getUserList(anyCollection())).thenReturn(List.of(new AdminUserRespDTO().setId(7L).setStatus(1)));
        service.dispatch(); verifyNoInteractions(notify);
    }
}
