package cn.iocoder.yudao.module.zsjos.service.task;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class BusinessTaskCancellationServiceTest {
 @Mock BusinessTaskMapper mapper;
 @InjectMocks BusinessTaskCancellationService service;
 @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
 @AfterEach void clear() { TenantContextHolder.clear(); }
 BusinessTaskDO task(String type, String status, long owner) {
  BusinessTaskDO t=new BusinessTaskDO(); t.setId(7L); t.setTenantId(1L); t.setTaskType(type);
  t.setStatus(status); t.setAssigneeId(owner); t.setAssigneeType("user"); return t;
 }
 @ParameterizedTest @ValueSource(strings={"sales_order_revision","lead_submitter_assist"})
 void cancelsOnlyOneTaskWithTrimmedReason(String type) {
  when(mapper.selectByIdForUpdate(7L,1L)).thenReturn(task(type,"pending",2));
  when(mapper.cancelMyPendingById(eq(7L),eq(1L),eq(2L),any(),eq("原因"))).thenReturn(1);
  assertTrue(service.cancel(7L,2L," 原因 "));
  verify(mapper).cancelMyPendingById(eq(7L),eq(1L),eq(2L),any(),eq("原因"));
  verifyNoMoreInteractions(mapper);
 }
 @ParameterizedTest @ValueSource(strings={"", "   ", "\t\n"})
 void rejectsBlankReason(String reason) {
  assertThrows(ServiceException.class,()->service.cancel(7L,2L,reason)); verifyNoInteractions(mapper);
 }
 @Test void rejectsLongReason() { assertThrows(ServiceException.class,()->service.cancel(7L,2L,"a".repeat(501))); verifyNoInteractions(mapper); }
 @Test void rejectsOtherOwner() {
  when(mapper.selectByIdForUpdate(7L,1L)).thenReturn(task("sales_order_revision","pending",3));
  assertThrows(ServiceException.class,()->service.cancel(7L,2L,"原因"));
  verify(mapper,never()).cancelMyPendingById(any(),any(),any(),any(),any());
 }
 @Test void rejectsOtherType() {
  when(mapper.selectByIdForUpdate(7L,1L)).thenReturn(task("lead_follow_up","pending",2));
  assertThrows(ServiceException.class,()->service.cancel(7L,2L,"原因"));
 }
 @Test void rejectsCompleted() {
  when(mapper.selectByIdForUpdate(7L,1L)).thenReturn(task("sales_order_revision","completed",2));
  assertThrows(ServiceException.class,()->service.cancel(7L,2L,"原因"));
 }
 @Test void preservesFirstCancellation() {
  when(mapper.selectByIdForUpdate(7L,1L)).thenReturn(task("sales_order_revision","cancelled",2));
  assertTrue(service.cancel(7L,2L,"第二次原因"));
  verify(mapper,never()).cancelMyPendingById(any(),any(),any(),any(),any());
 }
 @Test void missingTaskCannotMutate() { assertThrows(ServiceException.class,()->service.cancel(7L,2L,"原因")); }
 @Test void usesCurrentTenant() {
  TenantContextHolder.setTenantId(2L);
  assertThrows(ServiceException.class,()->service.cancel(7L,2L,"原因"));
  verify(mapper).selectByIdForUpdate(7L,2L);
 }
 @Test void detectsConcurrentTransition() {
  when(mapper.selectByIdForUpdate(7L,1L)).thenReturn(task("sales_order_revision","pending",2));
  assertThrows(ServiceException.class,()->service.cancel(7L,2L,"原因"));
 }
}
