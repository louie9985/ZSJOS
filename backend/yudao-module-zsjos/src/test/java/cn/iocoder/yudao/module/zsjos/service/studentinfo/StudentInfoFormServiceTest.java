package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StudentInfoFormServiceTest {
 @InjectMocks StudentInfoFormService service;
 @Mock StudentInfoFormMapper forms; @Mock StudentInfoFormValueMapper values; @Mock StudentInfoFormConfigMapper configs;
 @Mock StudentInfoConfigService configService; @Spy StudentInfoFields fields=new StudentInfoFields();
 @Mock RegistrationCaseMapper cases; @Mock SalesOrderMapper orders; @Mock RegistrationCommandMapper commands;
 @Mock StudentInfoImageService images; @Mock SecurityFrameworkService security; @Mock TenantFrameworkService tenants;
 final String token="a".repeat(43);
 @Test void pendingLegacyFormsUseCurrentAttachmentPolicyWithoutRewritingHistory(){
  var legacy=new StudentInfoFields().presets().stream().filter(field->"application_images".equals(field.getKey())).findFirst().orElseThrow();
  legacy.setAllowedExtensions(null);var draft=form("DRAFT");draft.setFieldsSnapshot(JsonUtils.toJsonString(List.of(legacy)));String snapshot=draft.getFieldsSnapshot();
  assertTrue(service.definitions(draft).getFirst().getAllowedExtensions().containsAll(List.of("pdf","doc","docx")));
  assertEquals(snapshot,draft.getFieldsSnapshot());draft.setStatus("SUBMITTED");assertNull(service.definitions(draft).getFirst().getAllowedExtensions());
  legacy.setAllowedExtensions(List.of("jpg","jpeg","png"));draft.setStatus("DRAFT");draft.setFieldsSnapshot(JsonUtils.toJsonString(List.of(legacy)));
  assertTrue(service.definitions(draft).getFirst().getAllowedExtensions().contains("docx"));
 }
 @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);TenantContextHolder.setIgnore(false);com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"test"),StudentInfoFormDO.class);}
 @AfterEach void cleanup(){TenantContextHolder.clear();}
 StudentInfoFormDO form(String status){var f=new StudentInfoFormDO();f.setId(12L);f.setRegistrationCaseId(5L);f.setTenantId(1L);f.setStatus(status);f.setTokenHash(StudentInfoFormService.hash(token));f.setTokenCiphertext(token);f.setConfigVersionId(3L);f.setExpiresAt(LocalDateTime.now().plusDays(1));f.setFieldsSnapshot(JsonUtils.toJsonString(List.of(new StudentInfoFields().presets().getFirst())));return f;}
 RegistrationCaseDO task(String status){var c=new RegistrationCaseDO();c.setId(5L);c.setOrderId(10L);c.setStatus(status);c.setVersion(2);return c;}
 void context(String status){var c=task(status);when(cases.selectById(5L)).thenReturn(c);when(cases.selectByIdForUpdate(5L,1L)).thenReturn(c);var o=new SalesOrderDO();o.setId(10L);o.setStatus("effective");lenient().when(orders.selectById(10L)).thenReturn(o);}
 Command command(){var c=new Command();c.setVersion(2);c.setFormId(12L);c.setIdempotencyKey("test-key");return c;}
 @Test void terminalResponseOmitsAnswers(){when(forms.byToken(anyString())).thenReturn(form("SUBMITTED"));var r=service.publicDetail(token);assertEquals("SUBMITTED",r.getStatus());assertTrue(r.getFields().isEmpty());assertTrue(r.getImages().isEmpty());assertNull(r.getTenantId());assertEquals(1L,TenantContextHolder.getTenantId());assertFalse(TenantContextHolder.isIgnore());}
 @Test void lookupFailureRestoresContext(){assertThrows(ServiceException.class,()->service.publicDetail(token));assertFalse(TenantContextHolder.isIgnore());assertEquals(1L,TenantContextHolder.getTenantId());assertThrows(ServiceException.class,()->service.publicDetail("short"));}
 @Test void duplicateSubmitIsSuccessfulWithoutWrites(){context("completed");var f=form("SUBMITTED");when(forms.byToken(anyString())).thenReturn(f);when(forms.lock(12L)).thenReturn(f);service.submit(token,new Submit());verifyNoInteractions(values,images);}
 @Test void completedFulfillmentStillAcceptsSubmission(){context("completed");var f=form("DRAFT");when(forms.byToken(anyString())).thenReturn(f);when(forms.lock(12L)).thenReturn(f);var req=new Submit();req.setValues(Map.of("name","合成测试"));service.submit(token,req);verify(values).insert(any(StudentInfoFormValueDO.class));var order=inOrder(orders,cases,forms,values);order.verify(orders).selectByIdForUpdate(10L,1L);order.verify(cases).selectByIdForUpdate(5L,1L);order.verify(forms).lock(12L);assertEquals(1L,TenantContextHolder.getTenantId());}
 @Test void endedTaskRejectsSubmission(){context("ended");var f=form("DRAFT");when(forms.byToken(anyString())).thenReturn(f);when(forms.lock(12L)).thenReturn(f);assertThrows(ServiceException.class,()->service.submit(token,new Submit()));verifyNoInteractions(values);}
 @Test void generationRetryKeepsToken(){context("processing");when(forms.byCase(5L)).thenReturn(form("DRAFT"));ReflectionTestUtils.setField(service,"publicBaseUrl","https://h5.example.test");assertEquals(12L,service.generate(5L,command()).getFormId());verifyNoInteractions(configService);verify(forms,never()).insert(any(StudentInfoFormDO.class));}
 @Test void staleVersionCannotRotate(){context("processing");var req=command();req.setVersion(1);assertThrows(ServiceException.class,()->service.regenerate(5L,req));verify(forms,never()).updateById(any(StudentInfoFormDO.class));}
 @Test void sentConfirmationDoesNotRequireSubmission(){context("processing");var f=form("DRAFT");when(forms.byCase(5L)).thenReturn(f);when(forms.lock(12L)).thenReturn(f);ReflectionTestUtils.setField(service,"publicBaseUrl","https://h5.example.test");var req=command();req.setSent(true);service.confirmSent(5L,req);verify(forms).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));verify(cases).updateById(any(RegistrationCaseDO.class));verifyNoInteractions(values);}
 @Test void differentTaskCannotReuseCommand(){context("processing");var old=new RegistrationCommandDO();old.setRegistrationCaseId(99L);when(commands.selectByIdempotencyKey("test-key")).thenReturn(old);assertThrows(ServiceException.class,()->service.generate(5L,command()));}
 @Test void oldTokenCannotCommitAfterRotation(){context("processing");when(forms.byToken(anyString())).thenReturn(form("DRAFT"));var rotated=form("DRAFT");rotated.setTokenHash("different");when(forms.lock(12L)).thenReturn(rotated);assertThrows(ServiceException.class,()->service.submit(token,new Submit()));verifyNoInteractions(values);}
 @Test void expiryAndTokenFormat(){var f=form("DRAFT");f.setExpiresAt(LocalDateTime.now().minusSeconds(1));assertEquals("EXPIRED",StudentInfoFormService.state(f));assertThrows(ServiceException.class,()->StudentInfoFormService.hash(null));}
 @Test void terminatedOrderCannotGenerate(){context("processing");var order=new SalesOrderDO();order.setStatus("terminated");when(orders.selectById(10L)).thenReturn(order);assertThrows(ServiceException.class,()->service.generate(5L,command()));verify(forms,never()).insert(any(StudentInfoFormDO.class));}
}
