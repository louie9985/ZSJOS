package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Submit;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
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
    @Mock StudentInfoFormMapper forms;
    @Mock StudentInfoFormValueMapper values;
    @Mock StudentInfoFormConfigMapper configs;
    @Mock StudentInfoConfigService configService;
    @Spy StudentInfoFields fields=new StudentInfoFields();
    @Mock LeadMapper leads;
    @Mock StudentInfoPermissionProvider permission;
    @Mock SecurityFrameworkService security;
    @Mock TenantFrameworkService tenants;
    private final String token="a".repeat(43);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L); TenantContextHolder.setIgnore(false);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "student-info-test"),
                StudentInfoFormDO.class);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private StudentInfoFormDO form(String status) {
        var row=new StudentInfoFormDO(); row.setId(12L); row.setLeadId(5L); row.setTenantId(8L);
        row.setStatus(status); row.setTokenHash(StudentInfoFormService.hash(token)); row.setTokenCiphertext(token);
        row.setConfigVersionId(3L); row.setExpiresAt(LocalDateTime.now().plusDays(1)); return row;
    }
    @Test void terminalPublicResponseOmitsFieldsAndRestoresTenant() {
        when(forms.byToken(anyString())).thenReturn(form("SUBMITTED"));
        var result=service.publicDetail(token);
        assertEquals("SUBMITTED",result.getStatus()); assertTrue(result.getFields().isEmpty()); assertNull(result.getTenantId());
        assertEquals(7L,TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
        verify(tenants).validTenant(8L); verifyNoInteractions(configs,values);
    }
    @Test void failedLookupRestoresIgnoreFlag() {
        assertThrows(ServiceException.class, () -> service.publicDetail(token));
        assertEquals(7L,TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
        assertThrows(ServiceException.class, () -> service.publicDetail("short"));
    }
    @Test void duplicateSubmitRejectsBeforeValueWrites() {
        var row=form("SUBMITTED"); when(forms.byToken(anyString())).thenReturn(row);
        when(leads.selectByIdForUpdate(5L,8L)).thenReturn(new LeadDO().setId(5L).setStatus("won"));
        when(forms.lock(12L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.submit(token,new Submit()));
        verifyNoInteractions(values,configs);
        assertEquals(7L,TenantContextHolder.getTenantId());
    }
    @Test void validSubmissionWritesInTokenTenantAndLocksLeadBeforeForm() {
        var row=form("DRAFT"); when(forms.byToken(anyString())).thenReturn(row);
        when(leads.selectByIdForUpdate(5L,8L)).thenReturn(new LeadDO().setId(5L).setStatus("won"));
        when(forms.lock(12L)).thenReturn(row);
        var config=new StudentInfoFormConfigDO(); config.setStatus("PUBLISHED");
        var name=fields.presets().stream().filter(f -> "name".equals(f.getKey())).findFirst().orElseThrow();
        config.setFieldsJson(JsonUtils.toJsonString(List.of(name))); when(configs.selectById(3L)).thenReturn(config);
        when(values.insert(any(StudentInfoFormValueDO.class))).thenAnswer(invocation -> {
            var answer=invocation.getArgument(0,StudentInfoFormValueDO.class);
            assertEquals(8L,TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
            assertEquals(8L,answer.getTenantId()); assertEquals(12L,answer.getFormId()); return 1;
        });
        when(forms.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var req=new Submit(); req.setValues(Map.of("name","测试填写")); service.submit(token,req);
        var order=inOrder(leads,forms,values);
        order.verify(leads).selectByIdForUpdate(5L,8L); order.verify(forms).lock(12L);
        order.verify(values).insert(any(StudentInfoFormValueDO.class));
        assertEquals(7L,TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
    }
    @Test void repeatedGenerationKeepsExistingLinkAndVersion() {
        when(leads.selectByIdForUpdate(5L,7L)).thenReturn(new LeadDO().setId(5L).setStatus("won"));
        when(forms.byLead(5L)).thenReturn(form("DRAFT"));
        ReflectionTestUtils.setField(service,"publicBaseUrl","https://example.test");
        assertEquals(12L,service.generate(5L).getFormId());
        verifyNoInteractions(configService); verify(forms,never()).insert(any(StudentInfoFormDO.class));
    }
    @Test void missingPublicUrlReturnsBusinessError() {
        when(forms.byLead(5L)).thenReturn(form("DRAFT"));
        ReflectionTestUtils.setField(service,"publicBaseUrl","");
        assertEquals(1_900_090_010,assertThrows(ServiceException.class, () -> service.getLink(5L)).getCode());
    }
    @Test void nonWonLeadCannotGenerateAndStaleRotationCannotInvalidateLink() {
        when(leads.selectByIdForUpdate(5L,7L)).thenReturn(new LeadDO().setId(5L).setStatus("valid"));
        assertThrows(ServiceException.class, () -> service.generate(5L));
        verifyNoInteractions(forms);
        when(leads.selectByIdForUpdate(5L,7L)).thenReturn(new LeadDO().setId(5L).setStatus("won"));
        when(forms.byLead(5L)).thenReturn(form("DRAFT"));
        assertEquals(1_900_090_003,assertThrows(ServiceException.class, () -> service.regenerate(5L,99L)).getCode());
        verify(forms,never()).updateById(any(StudentInfoFormDO.class));
    }
    @Test void detailUsesStoredLabelsAndHandlesOptionalNulls() {
        when(forms.submitted(5L)).thenReturn(form("SUBMITTED"));
        var config=new StudentInfoFormConfigDO(); config.setStatus("ARCHIVED"); config.setVersionNo(1);
        config.setFieldsJson(JsonUtils.toJsonString(fields.presets())); when(configs.selectById(3L)).thenReturn(config);
        var value=new StudentInfoFormValueDO(); value.setFieldKey("gender"); value.setValueLabelSnapshot("历史标签");
        var blank=new StudentInfoFormValueDO(); blank.setFieldKey("school");
        when(values.byForm(12L)).thenReturn(List.of(value,blank));
        var result=service.detail(5L);
        assertEquals("历史标签",result.getValues().get("gender")); assertEquals("",result.getValues().get("school"));
    }
    @Test void expiredLinkStateAndHashValidation() {
        var row=form("DRAFT"); row.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertEquals("EXPIRED",StudentInfoFormService.state(row));
        assertEquals(64,StudentInfoFormService.hash(token).length());
        assertThrows(ServiceException.class, () -> StudentInfoFormService.hash(null));
    }
}
