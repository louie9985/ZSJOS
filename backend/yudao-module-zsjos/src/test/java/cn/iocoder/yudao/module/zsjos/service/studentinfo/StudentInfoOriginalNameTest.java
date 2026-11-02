package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoImageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StudentInfoOriginalNameTest {
 @Test void namesSurviveUploadsReloadAndReuseWithoutChangingStorageKeys() throws Exception {
  var service=new StudentInfoImageService(); var mapper=mock(StudentInfoImageMapper.class);
  var forms=mock(StudentInfoFormService.class); var files=mock(FileApi.class);
  ReflectionTestUtils.setField(service,"images",mapper); ReflectionTestUtils.setField(service,"service",forms);
  ReflectionTestUtils.setField(service,"files",files); ReflectionTestUtils.setField(service,"key","synthetic-key");
  var form=new StudentInfoFormDO(); form.setId(1L); form.setTenantId(1L);
  var field=new StudentInfoFields().withAttachmentPolicy(new StudentInfoFields().presets()).stream().filter(f->"education_report".equals(f.getKey())).findFirst().orElseThrow();
  when(forms.definitions(form)).thenReturn(List.of(field)); when(forms.effectiveState(form)).thenReturn("DRAFT");
  when(mapper.byForm(1L)).thenReturn(List.of());
  doAnswer(c->((java.util.function.Function<StudentInfoFormDO,Object>)c.getArgument(1)).apply(form)).when(forms).withLockedToken(anyString(),any());
  var stored=new FileInfoRespDTO();stored.setId(8L);
  when(files.createFileInfo(any(byte[].class),anyString(),anyString(),anyString())).thenReturn(stored);
  List<StudentInfoImageDO> rows=new ArrayList<>();
  doAnswer(c->{StudentInfoImageDO row=c.getArgument(0);row.setId((long)rows.size()+1);rows.add(row);return 1;}).when(mapper).insert(any(StudentInfoImageDO.class));
  var file=new MockMultipartFile("file","学历报告.pdf","application/pdf","%PDF-1.7 synthetic".getBytes());
  TransactionSynchronizationManager.initSynchronization();
  try {
   assertEquals("学历报告.pdf",service.upload("synthetic-token","education_report","",file).getName());
   assertEquals("学历报告.pdf",service.archiveUpload(form,"education_report","",file).getName());
   when(mapper.byForm(1L)).thenReturn(List.of(rows.get(0)));
   assertEquals("学历报告.pdf",service.list(1L).get(0).getName());
   when(mapper.selectById(1L)).thenReturn(rows.get(0)); when(files.getFileContent(8L)).thenReturn(new byte[]{1,2});
   var target=new StudentInfoFormDO();target.setId(2L);target.setTenantId(1L);
   service.copyForCollection(form,target,service.list(1L),List.of(field));
   assertEquals("学历报告.pdf",rows.get(2).getOriginalName());
   var names=org.mockito.ArgumentCaptor.forClass(String.class);
   verify(files,times(3)).createFileInfo(any(byte[].class),names.capture(),anyString(),eq("application/octet-stream"));
   assertEquals(3,new HashSet<>(names.getAllValues()).size());
   names.getAllValues().forEach(n->{assertTrue(n.endsWith(".bin"));UUID.fromString(n.substring(0,n.length()-4));});
   rows.get(0).setOriginalName(null);assertEquals("education_report-1.pdf",service.list(1L).get(0).getName());
  } finally {TransactionSynchronizationManager.clearSynchronization();}
 }
 @Test void removesClientPathsAndControlCharactersOnly() {
  assertEquals("学历报告.pdf",StudentInfoImageService.originalName("C:\\fakepath\\学历报告.pdf"));
  assertEquals("report.pdf",StudentInfoImageService.originalName("/tmp/report.pdf"));
  assertEquals("report.pdf",StudentInfoImageService.originalName("report\r\n.pdf"));
  assertNull(StudentInfoImageService.originalName(null));
 }
}
