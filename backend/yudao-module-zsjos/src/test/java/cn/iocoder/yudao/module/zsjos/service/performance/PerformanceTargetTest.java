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
 @BeforeEach void allowHistory(){lenient().when(access.historicalRowAllowed(any(),any())).thenReturn(true);lenient().when(access.departmentAllowed(anyLong())).thenReturn(true);lenient().when(access.users(anyCollection())).thenAnswer(inv->((Collection<Long>)inv.getArgument(0)).stream().map(id->{var u=new AdminUserRespDTO();u.setId(id);u.setDeptId(10L);return u;}).toList());}
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
 PerformanceTargetDO row(String type,long id,String amount){var x=new PerformanceTargetDO();x.setId(id);x.setBusinessDomain("sales");x.setPeriodType("month");x.setPeriodStart(LocalDate.of(2026,9,1));x.setScopeType(type);x.setScopeId(id);x.setDeptId(10L);x.setCenterId(9L);x.setFloorAmount(new BigDecimal(amount));x.setSprintAmount(new BigDecimal(amount).multiply(BigDecimal.TWO));x.setManual(true);x.setVersion(0);return x;}
 @Test void organizationSingleDomainIsRetainedAndDualDomainReadIsSummedAndFlagged(){
  for(String type:List.of("DEPT","CENTER")) {
   var sales=row(type,10,"100");var delivery=row(type,10,"200");delivery.setId(20L);delivery.setBusinessDomain("delivery");
   when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(delivery));
   var single=service.rows("month",LocalDate.of(2026,9,1));assertEquals(new BigDecimal("200"),single.getFirst().getFloorAmount());assertFalse(single.getFirst().getSourceConflict());
   when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery));
   var merged=assertDoesNotThrow(()->service.rows("month",LocalDate.of(2026,9,1)));
   assertEquals(1,merged.size());assertEquals(new BigDecimal("300"),merged.getFirst().getFloorAmount());assertTrue(merged.getFirst().getSourceConflict());
   var target=service.resolve(type,10L,"month",LocalDate.of(2026,9,1));
   assertTrue(target.sourceConflict());assertEquals("10:0|20:0",target.sourceToken());
  }
 }
 @Test void staleMergeLinkIsFlaggedOnReadAndRejectedOnlyOnSave(){
  var sales=row("USER",1,"100");sales.setMergedDeliveryTargetId(99L);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales));
  var read=service.rows("month",LocalDate.of(2026,9,1)).getFirst();
  assertTrue(read.getSourceConflict());assertEquals(new BigDecimal("100"),read.getFloorAmount());
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);
  var edit=new TargetEdit();edit.setScopeType("USER");edit.setScopeId(1L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,9,1));edit.setFloorAmount(BigDecimal.ONE);edit.setSprintAmount(BigDecimal.TEN);edit.setReason("调整");edit.setSourceToken("1:0|-");
  var batch=new TargetBatch();batch.setItems(List.of(edit));
  var error=assertThrows(RuntimeException.class,()->service.save(batch));assertTrue(error.getMessage().contains("来源不一致"));
  verify(mapper,never()).updateById(any(PerformanceTargetDO.class));verifyNoInteractions(revisions);
 }
 @Test void concurrentFirstCreationReturnsRefreshConflictInsteadOfServerError(){
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
  var user=new AdminUserRespDTO();user.setId(1L);user.setDeptId(10L);when(access.user(1L)).thenReturn(user);
  when(mapper.insert(any(PerformanceTargetDO.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("uk_target"));
  var edit=new TargetEdit();edit.setScopeType("USER");edit.setScopeId(1L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,9,1));edit.setFloorAmount(BigDecimal.ONE);edit.setSprintAmount(BigDecimal.TEN);edit.setReason("首次");edit.setSourceToken("-|-");
  var batch=new TargetBatch();batch.setItems(List.of(edit));
  var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.save(batch));
  assertTrue(error.getMessage().contains("已被修改，请刷新"));verifyNoInteractions(revisions);
  reset(mapper);when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("deadlock",null));
  var deadlock=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.save(batch));
  assertTrue(deadlock.getMessage().contains("已被修改，请刷新"));
 }
 @Test void deliveryOnlyOrganizationSaveUpdatesExistingRowWithoutCreatingDuplicate(){
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);when(access.commandDepartmentAllowed(10L)).thenReturn(true);
  var delivery=row("DEPT",10,"200");delivery.setBusinessDomain("delivery");
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(delivery));
  var edit=new TargetEdit();edit.setScopeType("DEPT");edit.setScopeId(10L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,9,1));edit.setFloorAmount(BigDecimal.ONE);edit.setSprintAmount(BigDecimal.TEN);edit.setReason("调整");edit.setSourceToken("-|10:0");
  var batch=new TargetBatch();batch.setItems(List.of(edit));service.save(batch);
  assertEquals("delivery",delivery.getBusinessDomain());assertNull(delivery.getMergedDeliveryTargetId());verify(mapper).updateById(delivery);verify(mapper,never()).insert(any(PerformanceTargetDO.class));
 }
 @Test void organizationDualDomainSaveRejectsWithoutChangingEitherTarget(){
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);
  var sales=row("CENTER",10,"100");var delivery=row("CENTER",10,"200");delivery.setId(20L);delivery.setBusinessDomain("delivery");
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery));
  var edit=new TargetEdit();edit.setScopeType("CENTER");edit.setScopeId(10L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,9,1));edit.setFloorAmount(BigDecimal.ONE);edit.setSprintAmount(BigDecimal.TEN);edit.setReason("调整");edit.setSourceToken("10:0|20:0");
  var batch=new TargetBatch();batch.setItems(List.of(edit));assertThrows(RuntimeException.class,()->service.save(batch));
  verify(mapper,never()).updateById(any(PerformanceTargetDO.class));verify(mapper,never()).insert(any(PerformanceTargetDO.class));verifyNoInteractions(revisions);
 }
 @Test void userTargetKeepsStoredSnapshotInsteadOfCurrentDepartment(){
  var sales=row("USER",1,"100");var delivery=row("USER",1,"200");delivery.setId(2L);delivery.setBusinessDomain("delivery");delivery.setDeptId(11L);delivery.setCenterId(12L);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery));
  var rows=service.rows("month",LocalDate.of(2026,9,1));assertEquals(1,rows.size());
  assertEquals(10L,rows.getFirst().getDeptId());assertEquals(9L,rows.getFirst().getCenterId());
  verify(access,never()).users(anyCollection());verify(access,never()).mapping(any());
  var unassigned=row("USER",3,"100");unassigned.setDeptId(null);unassigned.setCenterId(null);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(unassigned));
  var kept=service.rows("month",LocalDate.of(2026,9,1)).getFirst();assertNull(kept.getDeptId());assertNull(kept.getCenterId());
 }
 @Test void bothDomainsMergeOnce(){
  var sales=row("USER",1,"100");sales.setPeriodType("month");sales.setPeriodStart(LocalDate.of(2026,9,1));
  var delivery=row("USER",1,"200");delivery.setId(2L);delivery.setBusinessDomain("delivery");delivery.setDeptId(11L);delivery.setPeriodType("month");delivery.setPeriodStart(sales.getPeriodStart());
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery));
  var merged=service.resolve("USER",1L,"month",sales.getPeriodStart());
  assertEquals(new BigDecimal("300"),merged.floorAmount());assertEquals("1:0|2:0",merged.sourceToken());
  sales.setFloorAmount(new BigDecimal("50"));sales.setMergedDeliveryTargetId(2L);sales.setVersion(1);
  var saved=service.resolve("USER",1L,"month",sales.getPeriodStart());
  assertEquals(new BigDecimal("50"),saved.floorAmount());assertEquals("1:1|2:0",saved.sourceToken());
 }
 @Test void mergedTargetContributesOnceToItsSnapshotDepartment(){
  var sales=row("USER",1,"100");sales.setPeriodType("month");sales.setPeriodStart(LocalDate.of(2026,9,1));
  var delivery=row("USER",1,"200");delivery.setId(2L);delivery.setBusinessDomain("delivery");delivery.setDeptId(11L);delivery.setPeriodType("month");delivery.setPeriodStart(sales.getPeriodStart());
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery));
  assertEquals(new BigDecimal("300"),service.resolve("DEPT",10L,"month",sales.getPeriodStart()).floorAmount());
  assertEquals(BigDecimal.ZERO,service.resolve("DEPT",11L,"month",sales.getPeriodStart()).floorAmount());
  assertEquals(BigDecimal.ZERO,service.resolve("DEPT",20L,"month",sales.getPeriodStart()).floorAmount());
 }
 @Test void changedDeliveryVersionRejectsStaleCombinedWrite(){
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);
  var sales=row("USER",1,"100");var delivery=row("USER",1,"200");delivery.setId(2L);delivery.setBusinessDomain("delivery");delivery.setVersion(1);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery));
  var edit=new TargetEdit();edit.setScopeType("USER");edit.setScopeId(1L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,9,1));edit.setFloorAmount(BigDecimal.ONE);edit.setSprintAmount(BigDecimal.TEN);edit.setSourceToken("1:0|2:0");
  var batch=new TargetBatch();batch.setItems(List.of(edit));
  assertThrows(RuntimeException.class,()->service.save(batch));verify(mapper,never()).updateById(any(PerformanceTargetDO.class));verifyNoInteractions(revisions);
 }
 @Test void savePersistsCombinedValueWithDeliveryLinkAndCurrentDepartment(){
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);
  var sales=row("USER",1,"100");var delivery=row("USER",1,"200");delivery.setId(2L);delivery.setBusinessDomain("delivery");
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sales,delivery));
  var user=new AdminUserRespDTO();user.setId(1L);user.setDeptId(20L);when(access.user(1L)).thenReturn(user);
  var org=new PerformanceOrgDO();org.setCenterId(30L);when(access.mapping(20L)).thenReturn(org);
  var edit=new TargetEdit();edit.setScopeType("USER");edit.setScopeId(1L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,9,1));edit.setFloorAmount(new BigDecimal("50"));edit.setSprintAmount(new BigDecimal("60"));edit.setReason("调整");edit.setSourceToken("1:0|2:0");
  var batch=new TargetBatch();batch.setItems(List.of(edit));service.save(batch);
  assertEquals(2L,sales.getMergedDeliveryTargetId());assertEquals(20L,sales.getDeptId());assertEquals(new BigDecimal("50"),sales.getFloorAmount());
  verify(mapper).updateById(sales);verify(revisions).insert(any(PerformanceRevisionDO.class));
 }
 @Test void manualDepartmentRetainedAndAutomaticDifferenceVisible(){when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row("USER",1,"100"),row("DEPT",10,"300")));var u=new AdminUserRespDTO();u.setId(1L);u.setDeptId(10L);u.setNickname("销售甲");when(access.sales()).thenReturn(List.of(u));when(access.user(1L)).thenReturn(u);var d=new DeptRespDTO();d.setName("一部");when(access.dept(10L)).thenReturn(d);var t=service.resolve("DEPT",10L,"month",LocalDate.of(2026,9,1));assertEquals(new BigDecimal("100"),t.automaticFloor());assertEquals(new BigDecimal("300"),t.floorAmount());assertTrue(t.manual());assertTrue(t.complete());}
 @Test void missingPersonIsNotZeroComplete(){when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());var t=service.resolve("USER",1L,"month",LocalDate.of(2026,9,1));assertFalse(t.complete());assertNull(t.floorAmount());assertEquals(1,t.missing());}
 @Test void missingRowsUseSameHierarchyAsCounterIncludingEmptyOrganization(){
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
  var start=LocalDate.of(2026,9,1);
  assertEquals(service.resolve("USER",1L,"month",start).missing(),service.missing("USER",1L,start).size());
  assertEquals(service.resolve("DEPT",10L,"month",start).missing(),service.missing("DEPT",10L,start).size());
  assertEquals("无可汇总的下级目标",service.missing("DEPT",10L,start).getFirst().reason());
 }
 @Test void quarterCarriesForwardUntilChangedWithoutRewritingPast(){
  var july=row("USER",1,"100");july.setPeriodStart(LocalDate.of(2026,7,1));
  var september=row("USER",2,"200");september.setScopeId(1L);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(july,september));
  var t=service.resolve("USER",1L,"quarter",LocalDate.of(2026,7,1));
  assertEquals(new BigDecimal("400"),t.floorAmount());assertTrue(t.complete());
  assertEquals(new BigDecimal("100"),service.resolve("USER",1L,"month",LocalDate.of(2026,8,1)).floorAmount());
 }
 @Test void nextMonthSaveInsertsAndPreservesPriorRecord(){
  var old=row("USER",1,"100");when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(old));
  when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);
  var user=new AdminUserRespDTO();user.setId(1L);user.setDeptId(10L);when(access.user(1L)).thenReturn(user);
  var edit=new TargetEdit();edit.setScopeType("USER");edit.setScopeId(1L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,10,1));edit.setFloorAmount(new BigDecimal("150"));edit.setSprintAmount(new BigDecimal("300"));edit.setReason("跨月调整");edit.setSourceToken("1:0|-");
  var batch=new TargetBatch();batch.setItems(List.of(edit));service.save(batch);
  verify(mapper).insert(argThat((PerformanceTargetDO x)->x.getPeriodStart().equals(LocalDate.of(2026,10,1))&&x.getFloorAmount().equals(new BigDecimal("150"))));
  verify(mapper,never()).updateById(any(PerformanceTargetDO.class));assertEquals(new BigDecimal("100"),old.getFloorAmount());assertEquals(LocalDate.of(2026,9,1),old.getPeriodStart());
 }

 @Test void invalidWeekRejectedBeforeAnyWrites(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);var edit=new TargetEdit();edit.setScopeType("USER");edit.setScopeId(1L);edit.setPeriodType("week");edit.setPeriodStart(LocalDate.of(2026,9,22));var batch=new TargetBatch();batch.setItems(List.of(edit));assertThrows(RuntimeException.class,()->service.save(batch));verifyNoInteractions(mapper,revisions);}
 @Test void wholeBatchAuthorizedBeforeWrites(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);var a=new TargetEdit();a.setScopeType("USER");a.setScopeId(1L);a.setPeriodType("month");a.setPeriodStart(LocalDate.of(2026,9,1));a.setFloorAmount(BigDecimal.ONE);a.setSprintAmount(BigDecimal.TEN);var b=new TargetEdit();b.setScopeType("USER");b.setScopeId(2L);doThrow(PerformanceAccess.denied()).when(access).targetWriteObject("USER",2L);var batch=new TargetBatch();batch.setItems(List.of(a,b));assertThrows(RuntimeException.class,()->service.save(batch));verifyNoInteractions(mapper,revisions);}
 @Test void restorePreservesRevisionAndTurnsOffManual(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);when(access.commandDepartmentAllowed(10L)).thenReturn(true);var existing=row("DEPT",10,"300");existing.setPeriodStart(LocalDate.of(2026,8,1));when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(existing));var edit=new TargetEdit();edit.setScopeType("DEPT");edit.setScopeId(10L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,8,1));edit.setVersion(0);edit.setSourceToken("10:0|-");edit.setRestoreAutomatic(true);edit.setReason("恢复原汇总");var batch=new TargetBatch();batch.setItems(List.of(edit));service.save(batch);assertFalse(existing.getManual());assertEquals(1,existing.getVersion());var rev=ArgumentCaptor.forClass(PerformanceRevisionDO.class);verify(revisions).insert(rev.capture());assertEquals("恢复原汇总",rev.getValue().getReason());assertTrue(rev.getValue().getBeforeJson().contains("300"));assertTrue(rev.getValue().getAfterJson().contains("false"));}
 @Test void staleVersionNeverOverwritesTarget(){when(access.has("zsjos:sales-performance-target:update")).thenReturn(true);when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row("DEPT",10,"300")));var edit=new TargetEdit();edit.setScopeType("DEPT");edit.setScopeId(10L);edit.setPeriodType("month");edit.setPeriodStart(LocalDate.of(2026,8,1));edit.setVersion(99);edit.setRestoreAutomatic(true);var batch=new TargetBatch();batch.setItems(List.of(edit));assertThrows(RuntimeException.class,()->service.save(batch));verify(mapper,never()).updateById(any(PerformanceTargetDO.class));verifyNoInteractions(revisions);}
 @Test void movedDepartmentDoesNotCarryHistoricalTargetsToNewCenter(){var existing=row("USER",1,"100");when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(existing));var mapping=new PerformanceOrgDO();mapping.setDeptId(10L);mapping.setCenterId(20L);mapping.setKind("DEPT");when(access.orgs()).thenReturn(List.of(mapping));var t=service.resolve("CENTER",20L,"month",LocalDate.of(2026,8,1));assertEquals(BigDecimal.ZERO,t.floorAmount());assertFalse(t.complete());}
}
