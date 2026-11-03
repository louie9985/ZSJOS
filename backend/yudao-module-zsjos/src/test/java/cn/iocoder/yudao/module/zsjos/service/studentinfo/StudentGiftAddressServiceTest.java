package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class StudentGiftAddressServiceTest {
 @InjectMocks StudentGiftAddressService service;
 @Mock StudentInfoFormMapper forms;
 @Mock StudentInfoFormValueMapper answers;
 @Mock StudentArchiveRevisionMapper revisions;
 @Test void missingArchiveDoesNotFallback() { assertNull(service.latestAddress(null)); verifyNoInteractions(forms, answers, revisions); }
 @Test void latestRevisionWinsAndEmptyDoesNotFallback() {
  var form=new StudentInfoFormDO(); form.setId(1L);
  when(forms.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(form);
  var revision=new StudentArchiveRevisionDO(); revision.setAnswersJson("[]");
  when(revisions.latest(1L)).thenReturn(revision);
  assertNull(service.latestAddress(9L)); verifyNoInteractions(answers);
  var value=new StudentInfoFormValueDO(); value.setFieldKey("gift_shipping_address"); value.setValueText("updated-address");
  revision.setAnswersJson(JsonUtils.toJsonString(List.of(value)));
  assertEquals("updated-address",service.latestAddress(9L));
 }
 @Test void originalSubmissionUsedWithoutRevision() {
  var form=new StudentInfoFormDO(); form.setId(1L);
  when(forms.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(form);
  var value=new StudentInfoFormValueDO(); value.setFieldKey("gift_shipping_address"); value.setValueText("collected-address");
  when(answers.byForm(1L)).thenReturn(List.of(value));
  assertEquals("collected-address",service.latestAddress(9L));
 }
}
