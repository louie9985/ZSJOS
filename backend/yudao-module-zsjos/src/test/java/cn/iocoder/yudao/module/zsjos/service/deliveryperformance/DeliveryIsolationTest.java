package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryPerformanceVO.OrgEdit;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryperformance.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryIsolationTest {
 @Test void salesPermissionCannotGrantDeliveryAccess() {
  var api=mock(PermissionApi.class);var access=new DeliveryPerformanceAccess();
  ReflectionTestUtils.setField(access,"permissionApi",api);
  try(var security=mockStatic(SecurityFrameworkUtils.class)){
   security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);
   when(api.hasAnyPermissions(1L,"zsjos:sales-performance:query")).thenReturn(true);
   assertFalse(access.has("zsjos:delivery-performance:query"));
   verify(api).hasAnyPermissions(1L,"zsjos:delivery-performance:query");
  }
 }
 @Test void salesTargetsCannotBeReadOrUpdatedByDeliveryProvider() {
  var mapper=mock(DeliveryPerformanceTargetMapper.class);var access=mock(DeliveryPerformanceAccess.class);
  var provider=new DeliveryPerformanceTargetPermissionProvider();
  ReflectionTestUtils.setField(provider,"mapper",mapper);ReflectionTestUtils.setField(provider,"access",access);
  var row=new DeliveryPerformanceTargetDO();row.setBusinessDomain("sales");when(mapper.selectById(7L)).thenReturn(row);
  assertEquals("delivery-performance-target",provider.getBizType());
  assertFalse(provider.hasPermission(7L,"read",1L));assertFalse(provider.hasPermission(7L,"update",1L));
  verifyNoInteractions(access);
  row.setBusinessDomain("delivery");assertTrue(provider.hasPermission(7L,"read",1L));verify(access).targetReadObject(row);
 }
 @Test void salesOrganizationCannotBeReassignedThroughDeliverySettings() {
  var mapper=mock(DeliveryPerformanceOrgMapper.class);var access=new DeliveryPerformanceAccess();
  ReflectionTestUtils.setField(access,"orgMapper",mapper);
  var row=new DeliveryPerformanceOrgDO();row.setBusinessDomain("sales");when(mapper.selectOne(any())).thenReturn(row);
  var req=new OrgEdit();req.setDeptId(10L);req.setCenterId(10L);req.setKind("CENTER");
  assertThrows(RuntimeException.class,()->access.saveOrg(req));
  verify(mapper,never()).insert(any(DeliveryPerformanceOrgDO.class));verify(mapper,never()).updateById(any(DeliveryPerformanceOrgDO.class));
 }
 @Test void organizationAndTargetQueriesAlwaysBindDeliveryDomain() {
  for(var type:java.util.List.of(DeliveryPerformanceOrgDO.class,DeliveryPerformanceTargetDO.class))
   TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"delivery-isolation"),type);
  var org=mock(DeliveryPerformanceOrgMapper.class);var access=new DeliveryPerformanceAccess();ReflectionTestUtils.setField(access,"orgMapper",org);
  when(org.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call->{
   var q=(com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?,?,?>)call.getArgument(0);
   assertTrue(q.getSqlSegment().contains("business_domain"));assertTrue(q.getParamNameValuePairs().containsValue("delivery"));return java.util.List.of();
  });access.orgs();
  var targets=mock(DeliveryPerformanceTargetMapper.class);var service=new DeliveryPerformanceTargetService();ReflectionTestUtils.setField(service,"mapper",targets);
  when(targets.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call->{
   var q=(com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?,?,?>)call.getArgument(0);
   assertTrue(q.getSqlSegment().contains("business_domain"));assertTrue(q.getParamNameValuePairs().containsValue("delivery"));return java.util.List.of();
  });service.rows("month",LocalDate.of(2026,10,1));
 }
}
