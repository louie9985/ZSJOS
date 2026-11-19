package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class PerformanceTargetBatchReviewTest {
 @InjectMocks PerformanceTargetService service;
 @Mock PerformanceTargetMapper mapper;
 @Mock PerformanceRevisionMapper revisions;
 @Mock PerformanceAccess access;
 @Test void listReadsTargetsOnceAndRetainsEnabledAuthorizedUsers() {
  var start=LocalDate.of(2026,10,1);var rows=new ArrayList<PerformanceTargetDO>();
  for(long i=1;i<=50;i++){var row=new PerformanceTargetDO();row.setBusinessDomain("sales");row.setScopeType("USER");row.setScopeId(i);row.setDeptId(10L);row.setPeriodType("month");row.setPeriodStart(start);rows.add(row);}
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(rows);
  lenient().when(access.users(anyCollection())).thenAnswer(inv->((Collection<Long>)inv.getArgument(0)).stream().map(id->{var u=new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO();u.setId(id);u.setDeptId(10L);return u;}).toList());
  when(access.targetVisibility()).thenReturn((id,dept)->true);lenient().when(access.departmentAllowed(anyLong())).thenReturn(true);
  when(access.tree(true)).thenReturn(rows.stream().map(x->new Node("USER:"+x.getScopeId(),null,"启用人员","USER",x.getScopeId(),true)).toList());
  var result=service.list("month",start);
  assertEquals(50,result.size());
  verify(mapper,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
  verify(access,times(1)).users(anyCollection());verify(access,never()).user(any());
 }
 @Test void hiddenHistoricalUserTargetStillContributesToDepartmentTotal() {
  var start=LocalDate.of(2026,10,1);var rows=new ArrayList<PerformanceTargetDO>();
  for(long id:List.of(1L,2L)) {
   var row=new PerformanceTargetDO();row.setBusinessDomain("sales");row.setScopeType("USER");row.setScopeId(id);row.setDeptId(10L);row.setCenterId(20L);
   row.setPeriodType("month");row.setPeriodStart(start);row.setFloorAmount(java.math.BigDecimal.valueOf(id*10));
   row.setSprintAmount(java.math.BigDecimal.valueOf(id*20));row.setManual(true);rows.add(row);
  }
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(rows);
  lenient().when(access.users(anyCollection())).thenAnswer(inv->((Collection<Long>)inv.getArgument(0)).stream().map(id->{var u=new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO();u.setId(id);u.setDeptId(10L);return u;}).toList());
  when(access.targetVisibility()).thenReturn((id,dept)->true);lenient().when(access.departmentAllowed(anyLong())).thenReturn(true);
  when(access.tree(true)).thenReturn(List.of(new Node("ORG:10",null,"销售部门","DEPT",10L,true),new Node("USER:1","ORG:10","启用人员","USER",1L,true)));
  var result=service.list("month",start);
  assertEquals(List.of(1L),result.stream().filter(x->"USER".equals(x.scopeType())).map(x->x.scopeId()).toList());
  var department=result.stream().filter(x->"DEPT".equals(x.scopeType())).findFirst().orElseThrow();
  assertEquals(java.math.BigDecimal.valueOf(30),department.floorAmount());
  assertEquals(java.math.BigDecimal.valueOf(60),department.sprintAmount());
  verify(mapper,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
 }
 @Test void conflictingOrganizationPeriodDoesNotBreakListAndIsFlagged() {
  var start=LocalDate.of(2026,10,1);
  var sales=new PerformanceTargetDO();sales.setId(1L);sales.setVersion(0);sales.setBusinessDomain("sales");sales.setScopeType("DEPT");sales.setScopeId(10L);sales.setDeptId(10L);sales.setPeriodType("month");sales.setPeriodStart(start);sales.setManual(true);sales.setFloorAmount(java.math.BigDecimal.valueOf(100));sales.setSprintAmount(java.math.BigDecimal.valueOf(200));
  var delivery=new PerformanceTargetDO();delivery.setId(2L);delivery.setVersion(3);delivery.setBusinessDomain("delivery");delivery.setScopeType("DEPT");delivery.setScopeId(10L);delivery.setDeptId(10L);delivery.setPeriodType("month");delivery.setPeriodStart(start);delivery.setManual(true);delivery.setFloorAmount(java.math.BigDecimal.valueOf(50));delivery.setSprintAmount(java.math.BigDecimal.valueOf(60));
  var person=new PerformanceTargetDO();person.setId(3L);person.setBusinessDomain("sales");person.setScopeType("USER");person.setScopeId(7L);person.setDeptId(10L);person.setPeriodType("month");person.setPeriodStart(start);person.setManual(true);person.setFloorAmount(java.math.BigDecimal.ONE);person.setSprintAmount(java.math.BigDecimal.TWO);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery,person));
  when(access.targetVisibility()).thenReturn((id,dept)->true);lenient().when(access.departmentAllowed(anyLong())).thenReturn(true);
  when(access.tree(true)).thenReturn(List.of(new Node("ORG:10",null,"销售部门","DEPT",10L,true),new Node("USER:7","ORG:10","员工","USER",7L,true)));
  var result=assertDoesNotThrow(()->service.list("month",start));
  var department=result.stream().filter(x->"DEPT".equals(x.scopeType())).findFirst().orElseThrow();
  assertTrue(department.sourceConflict());assertEquals(java.math.BigDecimal.valueOf(150),department.floorAmount());assertEquals("1:0|2:3",department.sourceToken());
  assertFalse(result.stream().filter(x->"USER".equals(x.scopeType())).findFirst().orElseThrow().sourceConflict());
 }
 @Test void revisionsFetchOperatorsOnceAndUseHistoricalObjectGuard() {
  var target=new PerformanceTargetDO();target.setId(1L);when(mapper.selectById(1L)).thenReturn(target);when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(target));
  var rows=new ArrayList<PerformanceRevisionDO>();
  for(long i=1;i<=50;i++){var row=new PerformanceRevisionDO();row.setOperatorId(i);rows.add(row);}
  when(revisions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(rows);
  assertEquals(50,service.revisionDetails(1L).size());
  verify(access,times(2)).targetReadObject(target);verify(access).users(anyCollection());verify(access,never()).user(any());
 }
}
