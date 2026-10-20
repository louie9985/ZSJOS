package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveExtraVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StudentArchiveExtraServiceTest {
    StudentArchiveExtraService service = new StudentArchiveExtraService();
    ServiceRelationMapper relations = mock(ServiceRelationMapper.class);
    StudentArchiveExtraRevisionMapper revisions = mock(StudentArchiveExtraRevisionMapper.class);
    StudentArchiveExtraConfigMapper config = mock(StudentArchiveExtraConfigMapper.class);
    StudentArchiveAttachmentMapper attachments = mock(StudentArchiveAttachmentMapper.class);
    PermissionApi permissions = mock(PermissionApi.class);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        for (var entry:Map.of("relations",relations,"revisions",revisions,"attachments",attachments,"configMapper",config,"permissions",permissions,"users",mock(AdminUserApi.class)).entrySet())
            ReflectionTestUtils.setField(service,entry.getKey(),entry.getValue());
        var relation = new ServiceRelationDO(); relation.setId(5L); relation.setPersonId(10L);
        when(relations.selectById(5L)).thenReturn(relation);
        when(relations.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(relation);
        when(config.current()).thenReturn(null);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    Value value(String text) { var v=new Value(); v.setText(text); return v; }
    Save request(String field,Value v) { var r=new Save(); r.setRevision(0); r.setIdempotencyKey("test"); r.setReason("synthetic correction"); r.setChanges(Map.of(field,v)); return r; }
    @Test void maintainBeforeAnyCollectionExists() {
        assertEquals(1,service.save(5L,request("channel",value("test channel"))));
        var captured=ArgumentCaptor.forClass(StudentArchiveExtraRevisionDO.class);verify(revisions).insert(captured.capture());
        assertEquals(5L,captured.getValue().getServiceRelationId());assertTrue(captured.getValue().getValuesJson().contains("test channel"));
    }
    @Test void emptyArchiveExposesIndependentMaintenance() {
        when(permissions.hasAnyPermissions(any(),any())).thenReturn(true);
        var detail=service.detail(5L);assertTrue(detail.getCanMaintain());assertEquals(11,detail.getFields().size());assertTrue(detail.getValues().isEmpty());
    }
    @Test void fieldVisibilityFiltersOnlyClosedFieldsForNonExamReaders() {
        var configRow = new StudentArchiveExtraConfigDO(); configRow.setRevision(3);
        configRow.setVisibilityJson(JsonUtils.toJsonString(Map.of("theory", false, "skill", true)));
        when(config.current()).thenReturn(configRow);
        var latest = new StudentArchiveExtraRevisionDO(); latest.setRevision(1);
        latest.setValuesJson(JsonUtils.toJsonString(Map.of("theory", value("hidden theory"), "skill", value("visible skill"))));
        when(revisions.latest(5L)).thenReturn(latest);
        when(permissions.hasAnyPermissions(any(), eq(cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.EXAM_READ))).thenReturn(false);
        when(permissions.hasAnyPermissions(any(), eq(cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.EXAM_VISIBILITY_UPDATE))).thenReturn(false);
        var detail = service.detail(5L);
        assertFalse(detail.getFields().stream().anyMatch(field -> field.key().equals("theory")));
        assertTrue(detail.getFields().stream().anyMatch(field -> field.key().equals("skill")));
        assertFalse(detail.getValues().containsKey("theory"));
        assertTrue(detail.getValues().containsKey("skill"));
        assertEquals(3, detail.getVisibilityRevision());
        assertFalse(detail.getCanConfigureVisibility());
    }
    @Test void visibilitySettingsUseRevisionAndConfiguredExamPermission() {
        when(permissions.hasAnyPermissions(any(), eq(cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.EXAM_VISIBILITY_UPDATE))).thenReturn(true);
        var row = new StudentArchiveExtraConfigDO(); row.setRevision(2); when(config.currentForUpdate()).thenReturn(row);
        var visibility = new LinkedHashMap<String,Boolean>(); StudentArchiveExtraService.definitions().forEach(field -> visibility.put(field.key(), !field.key().equals("theory")));
        var request = new VisibilitySave(); request.setRevision(2); request.setVisibility(visibility);
        assertEquals(3, service.saveVisibility(request));
        assertEquals(3, row.getRevision()); assertTrue(row.getVisibilityJson().contains("theory")); assertTrue(row.getVisibilityJson().contains("false"));
    }
    @Test void textLinksAndAttachmentAreSavedTogether() {
        when(permissions.hasAnyPermissions(any(),any())).thenReturn(true);
        var attachment=new StudentArchiveAttachmentDO();attachment.setId(7L);attachment.setServiceRelationId(5L);attachment.setFieldKey("theory");
        when(attachments.selectById(7L)).thenReturn(attachment);
        var v=value("85");v.setLinks(List.of("https://example.com/result"));v.setAttachmentIds(List.of(7L));
        assertEquals(1,service.save(5L,request("theory",v)));
        var captured=ArgumentCaptor.forClass(StudentArchiveExtraRevisionDO.class);verify(revisions).insert(captured.capture());
        String json=captured.getValue().getValuesJson();assertTrue(json.contains("85"));assertTrue(json.contains("https://example.com/result"));assertTrue(json.contains("7"));
    }
    @Test void attachmentFromAnotherServiceOrFieldRejected() {
        when(permissions.hasAnyPermissions(any(),any())).thenReturn(true);
        var a=new StudentArchiveAttachmentDO();a.setId(7L);a.setServiceRelationId(6L);a.setFieldKey("theory");when(attachments.selectById(7L)).thenReturn(a);
        var v=value("85");v.setAttachmentIds(List.of(7L));
        assertThrows(ServiceException.class,()->service.save(5L,request("theory",v)));a.setServiceRelationId(5L);a.setFieldKey("skill");
        assertThrows(ServiceException.class,()->service.save(5L,request("theory",v)));verify(revisions,never()).insert(any(StudentArchiveExtraRevisionDO.class));
    }
    @Test void staleRevisionCannotOverwrite() {
        var old=new StudentArchiveExtraRevisionDO();old.setRevision(1);when(revisions.latest(5L)).thenReturn(old);
        assertThrows(ServiceException.class,()->service.save(5L,request("channel",value("new"))));verify(revisions,never()).insert(any(StudentArchiveExtraRevisionDO.class));
    }
    @Test void replayMustHaveSameScopeOperatorAndPayload() {
        var req=request("channel",value("x"));var old=new StudentArchiveExtraRevisionDO();old.setServiceRelationId(5L);old.setRevision(1);
        old.setRequestHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(req)));
        when(revisions.byKey("test")).thenReturn(old);
        assertEquals(1,service.save(5L,req));old.setServiceRelationId(6L);assertThrows(ServiceException.class,()->service.save(5L,req));
    }
    @Test void sensitiveAddressAndAttachmentChangesDeniedIndependently() {
        assertThrows(ServiceException.class,()->service.save(5L,request("certificate_shipping_address",value("synthetic address"))));
        var v=value("85");v.setAttachmentIds(List.of(7L));assertThrows(ServiceException.class,()->service.save(5L,request("theory",v)));
    }
    @Test void textMaintenancePreservesOtherFieldsAndHistoricalReferences() {
        var old=new StudentArchiveExtraRevisionDO();old.setRevision(0);var v=value("old");v.setAttachmentIds(List.of(7L));
        old.setValuesJson(JsonUtils.toJsonString(Map.of("theory",v)));when(revisions.latest(5L)).thenReturn(old);
        service.save(5L,request("channel",value("new")));
        var capture=ArgumentCaptor.forClass(StudentArchiveExtraRevisionDO.class);verify(revisions).insert(capture.capture());
        assertTrue(capture.getValue().getValuesJson().contains("old"));assertTrue(capture.getValue().getValuesJson().contains("7"));
        verify(attachments,never()).deleteById(anyLong());
    }
    @Test void unsafeLinksAndInvalidFeesAreRejected() {
        var fee=StudentArchiveExtraService.definitions().stream().filter(f->f.key().equals("registration_fee")).findFirst().orElseThrow();
        StudentArchiveExtraService.validate(fee,value("123.45"));
        for(String invalid:List.of("-1","abc","1.234","1e3")) assertThrows(ServiceException.class,()->StudentArchiveExtraService.validate(fee,value(invalid)));
        var f=StudentArchiveExtraService.definitions().stream().filter(d->d.key().equals("theory")).findFirst().orElseThrow();
        for(String link:List.of("javascript:alert(1)","file:///a","https://user:pass@example.com","https:///missing")) {var v=value("85");v.setLinks(List.of(link));assertThrows(ServiceException.class,()->StudentArchiveExtraService.validate(f,v));}
    }
    @Test void collectedAddressFallbackPreservesManualOverride() {
        var relation=new ServiceRelationDO();relation.setPersonId(10L);relation.setOrderItemId(20L);relation.setRegistrationCaseId(30L);when(relations.selectById(5L)).thenReturn(relation);
        var forms=mock(StudentInfoFormMapper.class);var answers=mock(StudentInfoFormValueMapper.class);var originalRevisions=mock(StudentArchiveRevisionMapper.class);
        ReflectionTestUtils.setField(service,"forms",forms);ReflectionTestUtils.setField(service,"answers",answers);ReflectionTestUtils.setField(service,"collectionRevisions",originalRevisions);
        var form=new StudentInfoFormDO();form.setId(40L);form.setPersonId(10L);form.setOrderItemId(20L);form.setStatus("SUBMITTED");when(forms.byCase(30L)).thenReturn(form);
        var answer=new StudentInfoFormValueDO();answer.setFieldKey("certificate_shipping_address");answer.setValueText("collected");when(answers.byForm(40L)).thenReturn(List.of(answer));
        when(permissions.hasAnyPermissions(any(),any())).thenReturn(true);
        assertEquals("collected",service.detail(5L).getValues().get("certificate_shipping_address").getText());
        var manual=new StudentArchiveExtraRevisionDO();manual.setRevision(1);manual.setValuesJson(JsonUtils.toJsonString(Map.of("certificate_shipping_address",value("manual"))));when(revisions.latest(5L)).thenReturn(manual);
        assertEquals("manual",service.detail(5L).getValues().get("certificate_shipping_address").getText());
        when(permissions.hasAnyPermissions(any(),any())).thenReturn(false);
        assertEquals("已隐藏",service.detail(5L).getValues().get("certificate_shipping_address").getText());
    }
}
