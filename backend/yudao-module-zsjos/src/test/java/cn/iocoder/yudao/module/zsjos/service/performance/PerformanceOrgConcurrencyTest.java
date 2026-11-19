package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.OrgEdit;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceOrgMapper;
import cn.iocoder.yudao.module.zsjos.service.sales.SalesDepartmentBoundaryService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerformanceOrgConcurrencyTest {
 @Test void conflictingRootsSerializeThroughTransactionCompletion() throws Exception {
  var access=new PerformanceAccess();var permissions=mock(PermissionApi.class);var depts=mock(DeptApi.class);
  var mapper=mock(PerformanceOrgMapper.class);var boundary=mock(SalesDepartmentBoundaryService.class);
  var redis=mock(RedissonClient.class);var distributed=mock(RLock.class);var mutex=new ReentrantLock();
  ReflectionTestUtils.setField(access,"permissionApi",permissions);ReflectionTestUtils.setField(access,"deptApi",depts);
  ReflectionTestUtils.setField(access,"orgMapper",mapper);ReflectionTestUtils.setField(access,"salesBoundary",boundary);ReflectionTestUtils.setField(access,"redis",redis);
  when(permissions.hasAnyPermissions(any(),anyString())).thenReturn(true);
  var scope=new DeptDataPermissionRespDTO();scope.setAll(true);when(permissions.getDeptDataPermission(any())).thenReturn(scope);
  when(redis.getLock("zsjos:sales:boundary-config:1")).thenReturn(distributed);
  when(distributed.tryLock()).thenAnswer(x->mutex.tryLock());doAnswer(x->{mutex.unlock();return null;}).when(distributed).unlock();
  for(long id:List.of(10L,11L)){var dept=new DeptRespDTO();dept.setId(id);dept.setStatus(0);when(depts.getDept(id)).thenReturn(dept);}
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var committedRoot=new AtomicLong();
  doAnswer(call->{long dept=call.getArgument(0);if(committedRoot.get()!=0&&committedRoot.get()!=dept)throw PerformanceAccess.invalid("边界重叠");
   if(committedRoot.get()==0){entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));}return null;}).when(boundary).validateRootChange(anyLong(),eq(true));
  when(mapper.insert(any(PerformanceOrgDO.class))).thenAnswer(call->{PerformanceOrgDO row=call.getArgument(0);committedRoot.compareAndSet(0,row.getDeptId());return 1;});
  var executor=Executors.newSingleThreadExecutor();
  try {
   var first=executor.submit(()->run(access,10));assertTrue(entered.await(10,TimeUnit.SECONDS));
   assertThrows(RuntimeException.class,()->run(access,11));
   release.countDown();first.get(10,TimeUnit.SECONDS);
   assertThrows(RuntimeException.class,()->run(access,11));
   assertDoesNotThrow(()->run(access,10));
   verify(redis,times(4)).getLock("zsjos:sales:boundary-config:1");
  } finally {release.countDown();executor.shutdownNow();}
 }
 private static void run(PerformanceAccess access,long dept) {
  TenantContextHolder.setTenantId(1L);TransactionSynchronizationManager.initSynchronization();
  try {var req=new OrgEdit();req.setDeptId(dept);req.setCenterId(dept);req.setKind("CENTER");req.setSalesBoundaryRoot(true);access.saveOrg(req);}
  finally {for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
   TransactionSynchronizationManager.clearSynchronization();TenantContextHolder.clear();}
 }
}
