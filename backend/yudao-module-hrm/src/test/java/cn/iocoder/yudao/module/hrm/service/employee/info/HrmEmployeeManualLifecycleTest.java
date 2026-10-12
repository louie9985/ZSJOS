package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment.HrmEmployeeChangeRecordMapper;
import cn.iocoder.yudao.module.hrm.service.employee.api.HrmEmployeeLifecycleEventPublisher;
import cn.iocoder.yudao.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Import({HrmEmployeeManualLifecycle.class, HrmEmployeePlanService.class})
class HrmEmployeeManualLifecycleTest extends BaseDbUnitTest {
    @Resource HrmEmployeeManualLifecycle lifecycle;
    @Resource HrmEmployeePlanService plans;
    @Resource HrmEmployeeMapper employees;
    @Resource HrmEmployeeChangeRecordMapper records;
    @MockitoBean DictDataApi dictionaries;
    @MockitoBean HrmEmployeeLifecycleEventPublisher events;
    @MockitoBean HrmRecruitCandidateService candidates;
    @BeforeEach void dictionaries() {
        lenient().when(dictionaries.getDictDataList(anyString())).thenReturn(List.of(
                new DictDataRespDTO().setValue("1").setLabel("正式"),
                new DictDataRespDTO().setValue("2").setLabel("试用"),
                new DictDataRespDTO().setValue("4").setLabel("离职")));
    }
    private HrmEmployeeDO employee() {
        HrmEmployeeDO e = new HrmEmployeeDO().setName("合成员工").setMobile("13800000001").setJobNumber("TEST-HRM")
                .setType(1).setStatus(2).setEntryStatus(1).setProbation(3).setVersion(0L)
                .setEntryTime(LocalDateTime.now().minusMonths(3)).setPlannedRegularTime(LocalDateTime.now().minusDays(2));
        employees.insert(e); return employees.selectById(e.getId());
    }
    private HrmEmployeeSaveReqVO request(HrmEmployeeDO e) {
        return BeanUtils.toBean(e,HrmEmployeeSaveReqVO.class).setChangeMode("CORRECTION")
                .setOperationKey(UUID.randomUUID().toString()).setChangeReason("核实后纠正");
    }
    @Test void correctionPreservesManualStatusAndClearsDatesWithoutEvents() {
        var e=employee(); var r=request(e).setProbation(0).setPlannedRegularTime(null);
        var after=BeanUtils.toBean(r,HrmEmployeeDO.class);
        lifecycle.write(r,e,after);
        var saved=employees.selectById(e.getId());
        assertEquals(2,saved.getStatus()); assertEquals(0,saved.getProbation());
        assertNull(saved.getPlannedRegularTime()); assertNull(saved.getRegularTime());
        assertEquals(1L,saved.getVersion()); verifyNoInteractions(events,candidates);
        assertTrue(lifecycle.replay(r));
        r.setProbation(1); assertThrows(RuntimeException.class,()->lifecycle.replay(r));
    }
    @Test void businessRegularizationHasOneHistoryAndOneEvent() {
        var e=employee(); var r=request(e).setChangeMode("BUSINESS").setChangeType(4).setStatus(1)
                .setActualTime(LocalDateTime.now().minusDays(1));
        lifecycle.write(r,e,BeanUtils.toBean(r,HrmEmployeeDO.class));
        var saved=employees.selectById(e.getId()); assertEquals(1,saved.getStatus());
        assertEquals(r.getActualTime().toLocalDate(),saved.getRegularTime().toLocalDate());
        assertNull(saved.getPlannedRegularTime());
        assertTrue(lifecycle.replay(r));
        verify(events,times(1)).publish(any(),anyLong(),any(),eq(e.getId()));
        assertEquals(1,records.selectList().size());
    }
    @Test void staleVersionAndFutureActualDateAreRejected() {
        var e=employee(); var r=request(e).setVersion(99L);
        assertThrows(RuntimeException.class,()->lifecycle.write(r,e,BeanUtils.toBean(r,HrmEmployeeDO.class)));
        r.setVersion(0L).setChangeMode("BUSINESS").setChangeType(4).setStatus(1).setActualTime(LocalDateTime.now().plusDays(1));
        assertThrows(RuntimeException.class,()->lifecycle.write(r,e,BeanUtils.toBean(r,HrmEmployeeDO.class)));
        assertEquals(2,employees.selectById(e.getId()).getStatus()); verifyNoInteractions(events);
    }
    @Test void planDoesNotApplyTargetAndChangedArchiveBlocksConfirmation() {
        var e=employee(); var r=request(e).setChangeMode("PLAN").setChangeType(4).setStatus(1)
                .setPlannedRegularTime(LocalDateTime.now().plusDays(4));
        lifecycle.write(r,e,BeanUtils.toBean(r,HrmEmployeeDO.class));
        var plan=records.selectList().getFirst();
        var current=employees.selectById(e.getId()); assertEquals(2,current.getStatus()); assertNull(current.getRegularTime());
        var correction=request(current).setPostName("修正后的岗位");
        lifecycle.write(correction,current,BeanUtils.toBean(correction,HrmEmployeeDO.class));
        current=employees.selectById(e.getId()); var pending=records.selectById(plan.getId());
        var confirm=request(current).setChangeMode("BUSINESS").setChangeType(4).setStatus(1)
                .setPlanId(plan.getId()).setPlanVersion(pending.getVersion()).setActualTime(LocalDateTime.now().minusMinutes(1));
        var locked=current;
        assertThrows(RuntimeException.class,()->lifecycle.write(confirm,locked,BeanUtils.toBean(confirm,HrmEmployeeDO.class)));
        verifyNoInteractions(events);
    }

    @Test void cancelIsReplayableAndDoesNotChangeEmployeeStatus() {
        var e=employee(); var r=request(e).setChangeMode("PLAN").setChangeType(4).setStatus(1)
                .setPlannedRegularTime(LocalDateTime.now().plusDays(4));
        lifecycle.write(r,e,BeanUtils.toBean(r,HrmEmployeeDO.class));
        var plan=records.selectList().getFirst();
        var cancel=new cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.changerecord.HrmEmployeePlanCancelReqVO()
                .setId(plan.getId()).setEmployeeId(e.getId()).setVersion(plan.getVersion()).setEmployeeVersion(1L)
                .setOperationKey(UUID.randomUUID().toString()).setReason("计划取消");
        plans.cancel(cancel); plans.cancel(cancel);
        assertEquals("CANCELLED",records.selectById(plan.getId()).getState());
        assertEquals(2,employees.selectById(e.getId()).getStatus());
        assertNull(employees.selectById(e.getId()).getPlannedRegularTime());
        assertEquals(2,records.selectList().size()); verifyNoInteractions(events);
    }

    @Test void oldLoadedSnapshotCannotOverwriteAnotherWriter() {
        var e=employee(); var first=request(e).setProbation(1);
        lifecycle.write(first,e,BeanUtils.toBean(first,HrmEmployeeDO.class));
        var second=request(e).setProbation(2);
        assertThrows(RuntimeException.class,()->lifecycle.write(second,e,BeanUtils.toBean(second,HrmEmployeeDO.class)));
        assertEquals(1,employees.selectById(e.getId()).getProbation());
    }
}
