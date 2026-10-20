package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StudentInfoImageCleanupTest {
 @Test void onlyExpiredUnsubmittedOwnedNamespaceIsRemoved() throws Exception {
  var service=new StudentInfoImageService();var images=mock(StudentInfoImageMapper.class);var forms=mock(StudentInfoFormMapper.class);var files=mock(FileApi.class);
  ReflectionTestUtils.setField(service,"images",images);ReflectionTestUtils.setField(service,"forms",forms);ReflectionTestUtils.setField(service,"files",files);
  when(images.expiredFormIds()).thenReturn(List.of(1L,2L,3L,4L));
  for(long id=1;id<=4;id++){var form=new StudentInfoFormDO();form.setId(id);form.setStatus(id==2?"SUBMITTED":"DRAFT");form.setExpiresAt(LocalDateTime.now().minusDays(id==3?1:40));when(forms.lock(id)).thenReturn(form);}
  var owned=new StudentInfoImageDO();owned.setId(10L);owned.setInfraFileId(100L);var foreign=new StudentInfoImageDO();foreign.setId(40L);foreign.setInfraFileId(400L);
  when(images.byForm(1L)).thenReturn(List.of(owned));when(images.byForm(4L)).thenReturn(List.of(foreign));
  var metadata=new FileInfoRespDTO();metadata.setPath("student-info-private/1/synthetic.bin");when(files.getFileInfo(100L)).thenReturn(metadata);
  var other=new FileInfoRespDTO();other.setPath("other-domain/synthetic.bin");when(files.getFileInfo(400L)).thenReturn(other);
  assertEquals(1,service.cleanupExpired());verify(files).deleteFileIfExists(100L);verify(images).deleteById(10L);
  verify(files,never()).deleteFileIfExists(400L);verify(images,never()).byForm(2L);verify(images,never()).byForm(3L);
 }
}
