package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryperformance.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryPerformanceVO.*;
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
class DeliveryPerformanceTargetBatchReviewTest {
 @InjectMocks DeliveryPerformanceTargetService service;
 @Mock DeliveryPerformanceTargetMapper mapper;
 @Mock DeliveryPerformanceRevisionMapper revisions;
 @Mock DeliveryPerformanceAccess access;
 @Test void listReadsTargetsOnceAndRetainsExplicitHistoricalUsers() {
  var start=LocalDate.of(2026,10,1);var rows=new ArrayList<DeliveryPerformanceTargetDO>();
  for(long i=1;i<=50;i++){var row=new DeliveryPerformanceTargetDO();row.setScopeType("USER");row.setScopeId(i);row.setDeptId(10L);row.setPeriodType("month");row.setPeriodStart(start);rows.add(row);}
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(rows);
  when(access.targetVisibility()).thenReturn((id,dept)->true);
  var result=service.list("month",start);
  assertEquals(50,result.size());
  verify(mapper,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
  verify(access,times(1)).users(anyCollection());verify(access,never()).user(any());
 }
 @Test void revisionsFetchOperatorsOnceAndUseHistoricalObjectGuard() {
  var target=new DeliveryPerformanceTargetDO();target.setId(1L);when(mapper.selectById(1L)).thenReturn(target);
  var rows=new ArrayList<DeliveryPerformanceRevisionDO>();
  for(long i=1;i<=50;i++){var row=new DeliveryPerformanceRevisionDO();row.setOperatorId(i);rows.add(row);}
  when(revisions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(rows);
  assertEquals(50,service.revisionDetails(1L).size());
  verify(access).targetReadObject(target);verify(access).users(anyCollection());verify(access,never()).user(any());
 }
}
