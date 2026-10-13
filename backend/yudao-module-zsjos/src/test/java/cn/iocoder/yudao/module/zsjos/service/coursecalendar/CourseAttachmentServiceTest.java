package cn.iocoder.yudao.module.zsjos.service.coursecalendar;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class CourseAttachmentServiceTest {
 @Mock FileApi fileApi;
 @Mock PermissionApi permissionApi;
 @Mock CourseCalendarEventMapper mapper;
 @Mock CourseCalendarObjectPermissionProvider access;
 @InjectMocks CourseAttachmentService service;
 @BeforeEach void before(){TenantContextHolder.setTenantId(1L);}
 @AfterEach void after(){TenantContextHolder.clear();}
 void manage(){when(permissionApi.hasAnyPermissions(7L,"zsjos:course-calendar:manage")).thenReturn(true);}
 FileInfoRespDTO file(String path,String creator){return new FileInfoRespDTO(11L,1L,"lecture.pdf",path,"https://example.test/file","application/pdf",20L,creator);}
 @Test void uploadAndBindingDeniedBeforeStorage(){
  assertThrows(ServiceException.class,()->service.upload(new MockMultipartFile("file",new byte[]{1}),7L));
  assertThrows(ServiceException.class,()->service.validate(List.of(11L),null,7L));
  verifyNoInteractions(fileApi,mapper);
 }
 @Test void contentFilenameAndSizeValidated() throws Exception {
  manage();
  when(fileApi.createFileInfo(any(),eq("lecture.pdf"),eq("zsjos/course-attachment/1/7"),eq("application/pdf"))).thenReturn(file("zsjos/course-attachment/1/7/a.pdf","7"));
  when(fileApi.presignGetUrl(11L,3600)).thenReturn("https://example.test/signed");
  assertEquals(11L,service.upload(new MockMultipartFile("file","lecture.pdf","text/plain","%PDF-1.7\nlecture".getBytes()),7L).getFileId());
  assertThrows(ServiceException.class,()->service.upload(new MockMultipartFile("file","bad.png","image/png","<html>unsafe</html>".getBytes()),7L));
  assertThrows(ServiceException.class,()->service.upload(new MockMultipartFile("file","../a.pdf","application/pdf",new byte[]{1}),7L));
  assertThrows(ServiceException.class,()->service.upload(new MockMultipartFile("file","a.pdf","application/pdf",new byte[0]),7L));
  var large=mock(org.springframework.web.multipart.MultipartFile.class);when(large.getSize()).thenReturn(CourseAttachmentService.MAX_SIZE+1);
  assertThrows(ServiceException.class,()->service.upload(large,7L));
  verify(large,never()).getBytes();
 }
 @Test void newLinksRequireOwnNamespaceAndCreator(){
  manage();
  when(fileApi.getFileInfo(11L)).thenReturn(file("zsjos/course-attachment/1/7/a.pdf","7"));
  assertEquals("[11]",service.validate(List.of(11L),null,7L));
  for(String path:List.of("zsjos/course-attachment/1/8/a.pdf","zsjos/course-attachment/2/7/a.pdf","zsjos/exam-attachment/1/7/a.pdf")){
   when(fileApi.getFileInfo(11L)).thenReturn(file(path,"7"));
   assertThrows(ServiceException.class,()->service.validate(List.of(11L),null,7L));
  }
  when(fileApi.getFileInfo(11L)).thenReturn(file("zsjos/course-attachment/1/7/a.pdf","8"));
  assertThrows(ServiceException.class,()->service.validate(List.of(11L),null,7L));
 }
 @Test void omissionPreservesAndExplicitRemovalClearsExistingUnavailableFiles(){
  assertEquals("[11]",service.validate(null,"[11]",null));
  manage();assertEquals("[11]",service.validate(List.of(11L),"[11]",7L));
  assertEquals("[]",service.validate(List.of(),"[11]",7L));
  verifyNoInteractions(fileApi);
 }
 @Test void twentyBoundaryAndDuplicateValidation(){
  manage();
  List<Long> ids=java.util.stream.LongStream.rangeClosed(1,20).boxed().toList();
  assertEquals(ids,cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(service.validate(ids,ids.toString(),7L),Long.class));
  assertThrows(ServiceException.class,()->service.validate(java.util.stream.LongStream.rangeClosed(1,21).boxed().toList(),null,7L));
  assertThrows(ServiceException.class,()->service.validate(List.of(11L,11L),null,7L));
  var file=file("zsjos/course-attachment/1/7/a.pdf","7").setSize(CourseAttachmentService.MAX_SIZE);
  when(fileApi.getFileInfo(11L)).thenReturn(file);
  assertEquals("[11]",service.validate(List.of(11L),null,7L));
  file.setSize(CourseAttachmentService.MAX_SIZE+1);
  assertThrows(ServiceException.class,()->service.validate(List.of(11L),null,7L));
 }
 @Test void signedReadChecksCourseAndReferenceAndRejectsForeignNamespace(){
  when(mapper.selectById(5L)).thenReturn(new CourseCalendarEventDO().setAttachmentIdsJson("[11]"));
  when(fileApi.getFileInfo(11L)).thenReturn(file("zsjos/course-attachment/1/7/a.pdf","7"));
  when(fileApi.presignGetUrl(11L,3600)).thenReturn("https://example.test/signed");
  assertEquals("https://example.test/signed",service.read(5L,11L,7L).getUrl());
  assertThrows(ServiceException.class,()->service.read(5L,12L,7L));
  when(fileApi.getFileInfo(11L)).thenReturn(file("zsjos/course-attachment/2/7/a.pdf","7"));
  assertThrows(ServiceException.class,()->service.read(5L,11L,7L));
  verify(access,times(3)).check(5L,"read-attachment",7L);
  verify(fileApi,never()).getFileInfo(12L);
 }
}
