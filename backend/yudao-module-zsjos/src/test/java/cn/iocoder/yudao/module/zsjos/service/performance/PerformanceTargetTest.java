package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import java.time.*;
import java.math.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class) class PerformanceTargetTest {
 @InjectMocks PerformanceTargetService service; @Mock PerformanceTargetMapper mapper; @Mock PerformanceRevisionMapper revisions; @Mock PerformanceAccess access;
 @BeforeEach void allowHistory(){lenient().when(access.historicalRowAllowed(any(),any())).thenReturn(true);}
 @Test void disabledRecordedTargetStillContributesWithoutMissingUnconfiguredDisabledUsers(){
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row("USER",2,"100")));
  when(access.sales()).thenReturn(List.of());
  var result=service.resolve("DEPT",10L,"month",LocalDate.of(2026,9,1));
  assertEquals(new BigDecimal("100"),result.automaticFloor());assertTrue(result.complete());assertEquals(0,result.missing());
 }
 @Test void disabledMemberRejectsEntireBatchBeforeAnyWrites(){
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);
  var first=new TargetEdit();first.setScopeType("USER");first.setScopeId(1L);first.setPeriodType("month");first.setPeriodStart(LocalDate.of(2026,9,1));first.setFloorAmount(BigDecimal.ONE);first.setSprintAmount(BigDecimal.TEN);
  var disabled=new TargetEdit();disabled.setScopeType("USER");disabled.setScopeId(2L);
  doThrow(PerformanceAccess.invalid("该人员已停用")).when(access).targetWriteObject("USER",2L);
  var batch=new TargetBatch();batch.setItems(List.of(first,disabled));
  assertThrows(RuntimeException.class,()->service.save(batch));verifyNoInteractions(mapper,revisions);
 }
 PerformanceTargetDO row(String type,long id,String amount){var x=new PerformanceTargetDO();x.setId(id);x.setScopeType(type);x.setScopeId(id);x.setDeptId(10L);x.setCenterId(9L);x.setFloorAmount(new BigDecimal(amount));x.setSprintAmount(new BigDecimal(amount).multiply(BigDecimal.TWO));x.setManual(true);x.setVersion(0);return x;}
 @Test void manualDepartmentRetainedAndAutomaticDifferenceVisible(){when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row("USER",1,"100"),row("DEPT",10,"300")));var u=new AdminUserRespDTO();u.setId(1L);u.setDeptId(10L);u.setNickname("销售甲");when(access.sales()).thenReturn(List.of(u));when(access.user(1L)).thenReturn(u);var d=new DeptRespDTO();d.setName("一部");when(access.dept(10L)).thenReturn(d);var t=service.resolve("DEPT",10L,"month",LocalDate.of(2026,9,1));assertEquals(new BigDecimal("100"),t.automaticFloor());assertEquals(new BigDecimal("300"),t.floorAmount());assertTrue(t.manual());assertTrue(t.complete());}
 @Test void missingPersonIsNotZeroComplete(){when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());var t=service.resolve("USER",1L,"month",LocalDate.of(2026,9,1));assertFalse(t.complete());assertNull(t.floorAmount());assertEquals(1,t.missing());}
 @Test void quarterRequiresAllMonths(){when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row("USER",1,"100")),List.of(),List.of(row("USER",1,"200")));var t=service.resolve("USER",1L,"quarter",LocalDate.of(2026,7,1));assertEquals(new BigDecimal("300"),t.floorAmount());assertFalse(t.complete());assertEquals(1,t.missing());}
 @Test void invalidWeekRejectedBeforeAnyWrites(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);var edit=new TargetEdit();edit.setScopeType("USER");edit.setScopeId(1L);edit.setPeriodType("week");edit.setPeriodStart(LocalDate.of(2026,9,22));var batch=new TargetBatch();batch.setItems(List.of(edit));assertThrows(RuntimeException.class,()->service.save(batch));verifyNoInteractions(mapper,revisions);}
 @Test void wholeBatchAuthorizedBeforeWrites(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);var a=new TargetEdit();a.setScopeType("USER");a.setScopeId(1L);a.setPeriodType("month");a.setPeriodStart(LocalDate.of(2026,9,1));a.setFloorAmount(BigDecimal.ONE);a.setSprintAmount(BigDecimal.TEN);var b=new TargetEdit();b.setScopeType("USER");b.setScopeId(2L);doThrow(PerformanceAccess.denied()).when(access).targetWriteObject("USER",2L);var batch=new TargetBatch();batch.setItems(List.of(a,b));assertThrows(RuntimeException.class,()->service.save(batch));verifyNoInteractions(mapper,revisions);}
 @Test void restorePreservesRevisionAndTurnsOffManual(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);when(access.commandDepartmentAllowed(10L)).thenReturn(true);var existing=row("DEPT",10,"300");when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(existing);var edit=new TargetEdit();edit.setScopeType("DEPT");edit.setScopeId(10L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,8,1));edit.setVersion(0);edit.setRestoreAutomatic(true);edit.setReason("恢复原汇总");var batch=new TargetBatch();batch.setItems(List.of(edit));service.save(batch);assertFalse(existing.getManual());assertEquals(1,existing.getVersion());var rev=ArgumentCaptor.forClass(PerformanceRevisionDO.class);verify(revisions).insert(rev.capture());assertEquals("恢复原汇总",rev.getValue().getReason());assertTrue(rev.getValue().getBeforeJson().contains("300"));assertTrue(rev.getValue().getAfterJson().contains("false"));}
 @Test void staleVersionNeverOverwritesTarget(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row("DEPT",10,"300"));var edit=new TargetEdit();edit.setScopeType("DEPT");edit.setScopeId(10L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,8,1));edit.setVersion(99);edit.setRestoreAutomatic(true);var batch=new TargetBatch();batch.setItems(List.of(edit));assertThrows(RuntimeException.class,()->service.save(batch));verify(mapper,never()).updateById(any(PerformanceTargetDO.class));verifyNoInteractions(revisions);}
 @Test void movedDepartmentDoesNotCarryHistoricalTargetsToNewCenter(){var existing=row("USER",1,"100");when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(existing));var mapping=new PerformanceOrgDO();mapping.setDeptId(10L);mapping.setCenterId(20L);mapping.setKind("DEPT");when(access.orgs()).thenReturn(List.of(mapping));var t=service.resolve("CENTER",20L,"month",LocalDate.of(2026,8,1));assertEquals(BigDecimal.ZERO,t.floorAmount());assertFalse(t.complete());}
}
