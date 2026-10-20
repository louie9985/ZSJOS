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
 @Test void categoriesUseIdsAndSnapshotIsIndependent(){var f=new StudentInfoFields().presets();var rules=new StudentInfoProductRules();var ordinary=rules.resolve(f,List.of(7L),List.of(8L));assertEquals(29,ordinary.size());var education=rules.resolve(f,List.of(7L),List.of(8L,7L));assertEquals(32,education.size());assertTrue(education.stream().filter(x->StudentInfoProductRules.CONDITIONAL.contains(x.getKey())).allMatch(x->x.getRequired()));f.getFirst().setLabel("new");assertEquals("姓名",education.getFirst().getLabel());}
 @Test void onlyDecodableJpegPngAccepted()throws Exception{var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);assertEquals("image/png",StudentInfoImageService.validateImage(out.toByteArray(),"test.png"));assertThrows(ServiceException.class,()->StudentInfoImageService.validateImage(out.toByteArray(),"test.jpg"));assertThrows(ServiceException.class,()->StudentInfoImageService.validateImage(out.toByteArray(),"test.webp"));assertThrows(ServiceException.class,()->StudentInfoImageService.validateImage("fake".getBytes(),"test.png"));}
 @Test void imageEncryptionRoundTripAndTamperDetection(){var service=new StudentInfoImageService();ReflectionTestUtils.setField(service,"key","synthetic-test-key");byte[] raw="synthetic-image".getBytes();byte[] cipher=ReflectionTestUtils.invokeMethod(service,"crypt",raw,true);assertFalse(Arrays.equals(raw,cipher));assertArrayEquals(raw,ReflectionTestUtils.invokeMethod(service,"crypt",cipher,false));cipher[15]^=1;assertThrows(Exception.class,()->ReflectionTestUtils.invokeMethod(service,"crypt",cipher,false));}
 @Test void identitySidesAndCrossFormReferencesEnforced(){var service=new StudentInfoImageService();var mapper=mock(StudentInfoImageMapper.class);ReflectionTestUtils.setField(service,"images",mapper);var form=new StudentInfoFormDO();form.setId(1L);var f=new StudentInfoFields().presets().stream().filter(x->"identity_images".equals(x.getKey())).findFirst().orElseThrow();f.setRequired(true);var front=new StudentInfoImageDO();front.setId(11L);front.setFieldKey(f.getKey());front.setSide("front");var back=new StudentInfoImageDO();back.setId(12L);back.setFieldKey(f.getKey());back.setSide("back");when(mapper.byForm(1L)).thenReturn(List.of(front,back));service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11,12)));assertThrows(ServiceException.class,()->service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11))));assertThrows(ServiceException.class,()->service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11,99))));back.setSide("front");assertThrows(ServiceException.class,()->service.validateSubmission(form,List.of(f),Map.of(f.getKey(),List.of(11,12))));}
}
