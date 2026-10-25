package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoImageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StudentInfoImagesAndRulesTest {
 @Test void publicUploadDistinguishesEmptyAndOversizedAttachmentsBeforeReading() throws Exception {
  assertUploadSizeFailures(false,100);
 }
 @Test void archiveUploadDistinguishesEmptyAndOversizedAttachmentsUsingSnapshotLimit() throws Exception {
  assertUploadSizeFailures(true,10);
 }
 private void assertUploadSizeFailures(boolean archive,int maxSizeMb) throws Exception {
  var service=new StudentInfoImageService();var mapper=mock(StudentInfoImageMapper.class);
  var forms=mock(StudentInfoFormService.class);var files=mock(cn.iocoder.yudao.module.infra.api.file.FileApi.class);
  ReflectionTestUtils.setField(service,"images",mapper);ReflectionTestUtils.setField(service,"service",forms);ReflectionTestUtils.setField(service,"files",files);
  var form=new StudentInfoFormDO();form.setId(1L);form.setTenantId(1L);
  var field=new StudentInfoFields().presets().stream().filter(value->"application_images".equals(value.getKey())).findFirst().orElseThrow();field.setMaxSizeMb(maxSizeMb);
  when(forms.definitions(form)).thenReturn(List.of(field));when(forms.effectiveState(form)).thenReturn("DRAFT");when(mapper.byForm(1L)).thenReturn(List.of());
  doAnswer(call->((java.util.function.Function<StudentInfoFormDO,Object>)call.getArgument(1)).apply(form)).when(forms).withLockedToken(anyString(),any());
  java.util.function.Consumer<org.springframework.web.multipart.MultipartFile> upload=file->{
   if(archive) service.archiveUpload(form,"application_images","",file);
   else service.upload("synthetic-token","application_images","",file);
  };
  var empty=spy(new org.springframework.mock.web.MockMultipartFile("file","empty.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",new byte[0]));
  var emptyError=assertThrows(ServiceException.class,()->upload.accept(empty));
  assertEquals(1_900_090_012,emptyError.getCode());assertEquals("文件为空，请保存有效内容后重新上传",emptyError.getMessage());
  verify(empty,never()).getBytes();verify(empty,never()).getInputStream();
  var oversized=mock(org.springframework.web.multipart.MultipartFile.class);when(oversized.getSize()).thenReturn(maxSizeMb*1024L*1024+1);
  var oversizedError=assertThrows(ServiceException.class,()->upload.accept(oversized));
  assertEquals(1_900_090_013,oversizedError.getCode());assertEquals("单个附件不能超过 "+maxSizeMb+"MB",oversizedError.getMessage());
  assertNotEquals(emptyError.getCode(),oversizedError.getCode());verify(oversized,never()).getBytes();verify(oversized,never()).getInputStream();
  verifyNoInteractions(files);verify(mapper,never()).insert(any(StudentInfoImageDO.class));
 }
 @Test void collectionAccepts100MbButRejectsOneByteOverBeforeReading() throws Exception {
  var service=new StudentInfoImageService();var mapper=mock(StudentInfoImageMapper.class);
  var forms=mock(StudentInfoFormService.class);var files=mock(cn.iocoder.yudao.module.infra.api.file.FileApi.class);
  ReflectionTestUtils.setField(service,"images",mapper);ReflectionTestUtils.setField(service,"service",forms);ReflectionTestUtils.setField(service,"files",files);ReflectionTestUtils.setField(service,"key","synthetic-test-key");
  var form=new StudentInfoFormDO();form.setId(1L);form.setTenantId(1L);
  var definitions=new StudentInfoFields().withAttachmentPolicy(new StudentInfoFields().presets());
  when(forms.definitions(form)).thenReturn(definitions);when(forms.effectiveState(form)).thenReturn("DRAFT");when(mapper.byForm(1L)).thenReturn(List.of());
  doAnswer(call->((java.util.function.Function<StudentInfoFormDO,Object>)call.getArgument(1)).apply(form)).when(forms).withLockedToken(anyString(),any());
  var stored=new cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO();stored.setId(1L);when(files.createFileInfo(any(byte[].class),anyString(),anyString(),anyString())).thenReturn(stored);
  doAnswer(call->{((StudentInfoImageDO)call.getArgument(0)).setId(1L);return 1;}).when(mapper).insert(any(StudentInfoImageDO.class));
  var accepted=mock(org.springframework.web.multipart.MultipartFile.class);when(accepted.getSize()).thenReturn(100L*1024*1024);when(accepted.getOriginalFilename()).thenReturn("boundary.pdf");when(accepted.getBytes()).thenReturn("%PDF-1.7 synthetic".getBytes());
  org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
  try {assertEquals("application/pdf",service.upload("synthetic-token","application_images","",accepted).getContentType());}
  finally {org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
  var rejected=mock(org.springframework.web.multipart.MultipartFile.class);when(rejected.getSize()).thenReturn(100L*1024*1024+1);
  assertThrows(ServiceException.class,()->service.upload("synthetic-token","application_images","",rejected));verify(rejected,never()).getBytes();
 }
 @Test void categoriesUseIdsAndSnapshotIsIndependent(){var f=new StudentInfoFields().presets();var rules=new StudentInfoProductRules();var ordinary=rules.resolve(f,List.of(7L),List.of(8L));assertEquals(29,ordinary.size());var education=rules.resolve(f,List.of(7L),List.of(8L,7L));assertEquals(32,education.size());assertTrue(education.stream().filter(x->StudentInfoProductRules.CONDITIONAL.contains(x.getKey())).allMatch(x->x.getRequired()));f.getFirst().setLabel("new");assertEquals("姓名",education.getFirst().getLabel());}
 @Test void onlyDecodableJpegPngAccepted()throws Exception{var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);assertEquals("image/png",StudentInfoImageService.validateImage(out.toByteArray(),"test.png"));assertThrows(ServiceException.class,()->StudentInfoImageService.validateImage(out.toByteArray(),"test.jpg"));assertThrows(ServiceException.class,()->StudentInfoImageService.validateImage(out.toByteArray(),"test.webp"));assertThrows(ServiceException.class,()->StudentInfoImageService.validateImage("fake".getBytes(),"test.png"));}
 @Test void imageEncryptionRoundTripAndTamperDetection(){var service=new StudentInfoImageService();ReflectionTestUtils.setField(service,"key","synthetic-test-key");byte[] raw="synthetic-image".getBytes();byte[] cipher=ReflectionTestUtils.invokeMethod(service,"crypt",raw,true);assertFalse(Arrays.equals(raw,cipher));assertArrayEquals(raw,ReflectionTestUtils.invokeMethod(service,"crypt",cipher,false));cipher[15]^=1;assertThrows(Exception.class,()->ReflectionTestUtils.invokeMethod(service,"crypt",cipher,false));}
 @Test void identitySidesAndCrossFormReferencesEnforced(){var service=new StudentInfoImageService();var mapper=mock(StudentInfoImageMapper.class);ReflectionTestUtils.setField(service,"images",mapper);var form=new StudentInfoFormDO();form.setId(1L);var f=new StudentInfoFields().presets().stream().filter(x->"identity_images".equals(x.getKey())).findFirst().orElseThrow();f.setRequired(true);var front=new StudentInfoImageDO();front.setId(11L);front.setFieldKey(f.getKey());front.setSide("front");var back=new StudentInfoImageDO();back.setId(12L);back.setFieldKey(f.getKey());back.setSide("back");when(mapper.byForm(1L)).thenReturn(List.of(front,back));service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11,12)));assertThrows(ServiceException.class,()->service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11))));assertThrows(ServiceException.class,()->service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11,99))));back.setSide("front");assertThrows(ServiceException.class,()->service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11,12))));}
}
