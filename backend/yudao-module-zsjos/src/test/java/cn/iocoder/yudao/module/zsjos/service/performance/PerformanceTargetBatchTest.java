package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.*;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PerformanceTargetBatchTest {
 @Test void yearlyQuarterlyAndMonthlyTargetsShareOneReadAndPreserveManualHistoricalRules(){
  var mapper=mock(PerformanceTargetMapper.class);var access=mock(PerformanceAccess.class);var service=new PerformanceTargetService();
  ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"access",access);
  var rows=new ArrayList<PerformanceTargetDO>();
  for(int month=1;month<=12;month++){
   var person=new PerformanceTargetDO();person.setScopeType("USER");person.setScopeId(2L);person.setDeptId(10L);person.setCenterId(20L);person.setPeriodType("month");person.setPeriodStart(LocalDate.of(2026,month,1));person.setFloorAmount(BigDecimal.TEN);person.setSprintAmount(BigDecimal.valueOf(20));person.setManual(true);rows.add(person);
  }
  var manual=new PerformanceTargetDO();manual.setScopeType("DEPT");manual.setScopeId(10L);manual.setDeptId(10L);manual.setCenterId(20L);manual.setPeriodType("month");manual.setPeriodStart(LocalDate.of(2026,1,1));manual.setManual(true);manual.setFloorAmount(BigDecimal.valueOf(50));manual.setSprintAmount(BigDecimal.valueOf(60));rows.add(manual);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(rows);
  var org=new PerformanceOrgDO();org.setKind("DEPT");org.setDeptId(10L);org.setCenterId(20L);when(access.orgs()).thenReturn(List.of(org));
  when(access.targetVisibility()).thenReturn((user,dept)->true);
  var historical=new AdminUserRespDTO();historical.setId(2L);historical.setNickname("历史销售");historical.setStatus(1);when(access.users(anyCollection())).thenReturn(List.of(historical));
  var department=new DeptRespDTO();department.setId(10L);department.setName("部门");when(access.departments(anyCollection())).thenReturn(List.of(department));
  var batch=service.batch(List.of(new PerformanceTargetService.Period("year",LocalDate.of(2026,1,1))),List.of());
  var year=batch.resolve("CENTER",20L,"year",LocalDate.of(2026,1,1));
  assertEquals(BigDecimal.valueOf(160),year.floorAmount());assertTrue(year.complete());
  var quarter=batch.resolve("CENTER",20L,"quarter",LocalDate.of(2026,1,1));assertEquals(BigDecimal.valueOf(70),quarter.floorAmount());
  var month=batch.resolve("DEPT",10L,"month",LocalDate.of(2026,1,1));assertEquals(BigDecimal.TEN,month.automaticFloor());assertEquals(BigDecimal.valueOf(50),month.floorAmount());assertTrue(month.manual());
  assertSame(month,batch.resolve("DEPT",10L,"month",LocalDate.of(2026,1,1)));
  verify(mapper,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));verify(access,times(1)).sales();verify(access,times(1)).orgs();verify(access,times(1)).users(anyCollection());verify(access,times(1)).departments(anyCollection());verify(access,never()).user(any());verify(access,never()).historicalRowAllowed(any(),any());
 }
 @Test void deniedHistoricalUserTargetRemainsMissing(){
  var mapper=mock(PerformanceTargetMapper.class);var access=mock(PerformanceAccess.class);var service=new PerformanceTargetService();ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"access",access);
  var row=new PerformanceTargetDO();row.setScopeType("USER");row.setScopeId(2L);row.setDeptId(99L);row.setPeriodType("month");row.setPeriodStart(LocalDate.of(2026,1,1));row.setFloorAmount(BigDecimal.TEN);row.setManual(true);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row));when(access.targetVisibility()).thenReturn((user,dept)->false);
  var batch=service.batch(List.of(new PerformanceTargetService.Period("month",row.getPeriodStart())),List.of(2L));
  var result=batch.resolve("USER",2L,"month",row.getPeriodStart());assertEquals(1,result.missing());assertNull(result.floorAmount());assertFalse(result.complete());
 }
}
