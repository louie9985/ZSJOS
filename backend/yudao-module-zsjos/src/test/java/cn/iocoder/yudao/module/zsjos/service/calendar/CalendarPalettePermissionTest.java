package cn.iocoder.yudao.module.zsjos.service.calendar;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarPaletteMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermissionAspect;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarColorSaveReqVO;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CalendarPalettePermissionTest {
 CalendarPaletteMapper mapper;
 PermissionApi grants;
 CalendarPaletteService proxy;
 MockedStatic<SecurityFrameworkUtils> security;
 @BeforeEach void setup() {
  TenantContextHolder.setTenantId(1L);
  security=mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
  mapper=mock(CalendarPaletteMapper.class); grants=mock(PermissionApi.class);
  var provider=new CalendarPalettePermissionProvider(); ReflectionTestUtils.setField(provider,"permissions",grants);
  var target=new CalendarPaletteService(); ReflectionTestUtils.setField(target,"mapper",mapper); ReflectionTestUtils.setField(target,"access",provider);
  var factory=new AspectJProxyFactory(target); factory.addAspect(new ZsjosPermissionAspect(List.of(provider))); proxy=factory.getProxy();
 }
 @AfterEach void close(){security.close();TenantContextHolder.clear();}
 @Test void ownerAndCalendarSpecificPermissionsAreCumulative() {
  assertThrows(ServiceException.class,()->proxy.get("PERSONAL",7L));
  when(grants.hasAnyPermissions(7L,"zsjos:personal-calendar:query")).thenReturn(true);
  assertTrue(proxy.get("PERSONAL",7L).colors().isEmpty());
  assertThrows(ServiceException.class,()->proxy.get("PERSONAL",8L));
  assertThrows(ServiceException.class,()->proxy.get("COURSE",7L));
  assertThrows(ServiceException.class,()->proxy.mutate("PERSONAL",7L,null,new CalendarColorSaveReqVO().setName("x").setColor("#112233"),false));
  verify(mapper,never()).ensure(anyLong(),anyLong(),anyString());
 }
 @Test void coursePresetsComeFromServerAndOtherTypesAreDenied() {
  when(grants.hasAnyPermissions(7L,"zsjos:course-calendar:query")).thenReturn(true);
  assertThrows(ServiceException.class,()->proxy.get("COURSE",7L));
  when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(
    new cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarPaletteDO().setColorsJson("[]"));
  assertTrue(proxy.get("COURSE",7L).colors().isEmpty());
  assertTrue(proxy.get("COURSE",7L).presets().isEmpty());
  assertThrows(ServiceException.class,()->proxy.get("EXAM",7L));
 }
 @Test void courseCreationPermissionAllowsSharedPaletteEditing() {
  when(grants.hasAnyPermissions(7L,"zsjos:course-calendar:manage")).thenReturn(true);
  when(mapper.lock(1L,0L,"COURSE_SHARED")).thenReturn(
    new cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarPaletteDO().setId(2L).setColorsJson("[]"));
  assertEquals(1,proxy.mutate("COURSE",7L,null,new CalendarColorSaveReqVO().setName("共享").setColor("#112233"),false).colors().size());
  verify(mapper,never()).ensure(anyLong(),anyLong(),anyString());
  verify(grants,never()).hasAnyPermissions(7L,"zsjos:course-calendar:color-manage");
 }
}
