package cn.iocoder.yudao.module.zsjos.service.coursecalendar;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermissionAspect;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CourseAttachmentPermissionTest {
 CourseCalendarEventMapper mapper; PermissionApi grants; FileApi files; CourseAttachmentService proxy; CourseCalendarEventDO row;
 MockedStatic<SecurityFrameworkUtils> security;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);security=mockStatic(SecurityFrameworkUtils.class);security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
  mapper=mock(CourseCalendarEventMapper.class);grants=mock(PermissionApi.class);files=mock(FileApi.class);
  row=new CourseCalendarEventDO().setId(5L).setAttachmentIdsJson("[11]");row.setTenantId(1L);row.setDeleted(false);
  when(mapper.selectById(5L)).thenReturn(row);
  var access=new CourseCalendarObjectPermissionProvider();ReflectionTestUtils.setField(access,"mapper",mapper);ReflectionTestUtils.setField(access,"permissionApi",grants);
  var target=new CourseAttachmentService();ReflectionTestUtils.setField(target,"mapper",mapper);ReflectionTestUtils.setField(target,"access",access);ReflectionTestUtils.setField(target,"fileApi",files);
  var factory=new AspectJProxyFactory(target);factory.addAspect(new ZsjosPermissionAspect(List.of(access)));proxy=factory.getProxy();
 }
 @AfterEach void close(){security.close();TenantContextHolder.clear();}
 @Test void queryAllowsReadingBoundFilesWithoutManage(){
  when(grants.hasAnyPermissions(7L,"zsjos:course-calendar:query")).thenReturn(true);
  when(files.getFileInfo(11L)).thenReturn(new FileInfoRespDTO(11L,1L,"file.pdf","zsjos/course-attachment/1/8/file.pdf","url","application/pdf",20L,"8"));
  when(files.presignGetUrl(11L,3600)).thenReturn("https://example.test/signed");
  assertEquals("https://example.test/signed",proxy.read(5L,11L,7L).getUrl());
  assertThrows(ServiceException.class,()->proxy.read(5L,12L,7L));
  verify(grants,never()).hasAnyPermissions(7L,"zsjos:course-calendar:manage");
 }
 @Test void deniedDeletedAndForeignTenantNeverReachFiles(){
  assertThrows(ServiceException.class,()->proxy.read(5L,11L,7L));
  row.setDeleted(true);assertThrows(ServiceException.class,()->proxy.read(5L,11L,7L));
  row.setDeleted(false);row.setTenantId(2L);assertThrows(ServiceException.class,()->proxy.read(5L,11L,7L));
  verifyNoInteractions(files);
 }
}
