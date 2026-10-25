package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class StudentArchiveServiceTest {
    StudentArchiveService service;
    ServiceRelationMapper relations=mock(ServiceRelationMapper.class);
    StudentInfoFormMapper forms=mock(StudentInfoFormMapper.class);
    StudentInfoFormValueMapper answers=mock(StudentInfoFormValueMapper.class);
    StudentArchiveRevisionMapper revisions=mock(StudentArchiveRevisionMapper.class);
    StudentInfoImageMapper imageMapper=mock(StudentInfoImageMapper.class);
    StudentInfoFormService collection=mock(StudentInfoFormService.class);
    StudentInfoFields fields=mock(StudentInfoFields.class);
    StudentInfoImageService images=mock(StudentInfoImageService.class);
    PermissionApi permissions=mock(PermissionApi.class);
    StudentInfoFormDO form;
    @BeforeEach void setup(){
        service=new StudentArchiveService();
        ReflectionTestUtils.setField(service,"configs",mock(StudentInfoFormConfigMapper.class));
        ReflectionTestUtils.setField(service,"users",mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class));
        for(var entry:Map.of("relations",relations,"forms",forms,"answers",answers,"revisions",revisions,"imageMapper",imageMapper,"collection",collection,"fields",fields,"images",images,"permissions",permissions).entrySet()) ReflectionTestUtils.setField(service,entry.getKey(),entry.getValue());
        var relation=new ServiceRelationDO();relation.setId(5L);relation.setPersonId(10L);relation.setOrderItemId(20L);relation.setRegistrationCaseId(30L);
        when(relations.selectById(5L)).thenReturn(relation);
        form=new StudentInfoFormDO();form.setId(40L);form.setPersonId(10L);form.setOrderItemId(20L);form.setStatus("SUBMITTED");form.setTenantId(1L);
        when(forms.byCase(30L)).thenReturn(form);when(forms.lock(40L)).thenReturn(form);
        when(answers.byForm(40L)).thenReturn(List.of());when(images.list(40L)).thenReturn(List.of());
    }
    StudentInfoVO.Field field(String key,String type){var f=new StudentInfoVO.Field();f.setKey(key);f.setLabel(key);f.setType(type);f.setEnabled(true);f.setRequired(false);f.setSensitive(false);return f;}
    Save request(int revision){var r=new Save();r.setRevision(revision);r.setReason("correct");r.setIdempotencyKey("command");r.setChanges(Map.of("job","teacher"));return r;}
    @org.junit.jupiter.api.BeforeEach void teachingTenant() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        org.mockito.Mockito.lenient().when(relations.selectByIdForUpdate(anyLong(),eq(1L))).thenAnswer(call -> relations.selectById(call.getArgument(0,Long.class)));
    }
    @org.junit.jupiter.api.AfterEach void clearTeachingTenant() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    @Test void mismatchedStudentOrOrderItemRejected(){form.setPersonId(99L);assertThrows(RuntimeException.class,()->service.detail(5L,false));verifyNoInteractions(answers,revisions);}
    @Test void unsubmittedCannotBeMaintained(){form.setStatus("DRAFT");assertThrows(RuntimeException.class,()->service.edit(5L));verifyNoInteractions(revisions);}
    @Test void staleRevisionDoesNotWrite(){var latest=new StudentArchiveRevisionDO();latest.setRevision(2);when(revisions.latest(40L)).thenReturn(latest);assertThrows(RuntimeException.class,()->service.save(5L,request(0)));verify(revisions,never()).insert(any(StudentArchiveRevisionDO.class));}
    @Test void unchangedDictionarySnapshotAndOriginalSubmissionRetained(){
        var dict=new StudentInfoFormValueDO();dict.setFieldKey("education_level");dict.setFieldType("dict");dict.setValueCode("old");dict.setValueLabelSnapshot("historic label");
        when(answers.byForm(40L)).thenReturn(List.of(dict));when(collection.definitions(form)).thenReturn(List.of(field("job","text"),field("education_level","dict")));
        var job=new StudentInfoFormValueDO();job.setFieldKey("job");job.setValueText("teacher");when(fields.validateValues(anyList(),anyMap())).thenReturn(List.of(job));
        assertEquals(1,service.save(5L,request(0)));
        var capture=ArgumentCaptor.forClass(StudentArchiveRevisionDO.class);verify(revisions).insert(capture.capture());
        assertTrue(capture.getValue().getAnswersJson().contains("historic label"));verify(answers,never()).updateById(any(StudentInfoFormValueDO.class));verify(answers,never()).deleteById(anyLong());
    }
    @Test void sensitiveChangesRequireIndependentPermission(){var f=field("job","text");f.setSensitive(true);when(collection.definitions(form)).thenReturn(List.of(f));assertThrows(RuntimeException.class,()->service.save(5L,request(0)));verify(revisions,never()).insert(any(StudentArchiveRevisionDO.class));}
    @Test void registrationSensitivePermissionDoesNotGrantArchiveAccess() {
        when(permissions.hasAnyPermissions(any(), eq("zsjos:student-info-form:sensitive-read"))).thenReturn(true);
        assertFalse(service.detail(5L,false).getInformation().getCanReadSensitive());
        assertThrows(RuntimeException.class, () -> service.detail(5L,true));
        assertThrows(RuntimeException.class, () -> service.image(5L,100L));
        verify(images,never()).archiveRead(anyLong(),anyLong());
    }
    @Test void archiveSensitivePermissionReadsDetailsAndOnlyMatchingAttachments() {
        when(permissions.hasAnyPermissions(any(), eq("zsjos:student-archive:sensitive-read"))).thenReturn(true);
        var value=new StudentInfoFormValueDO(); value.setFieldKey("id_card"); value.setSensitive(true); value.setValueText("sensitive-value");
        when(answers.byForm(40L)).thenReturn(List.of(value));
        var detail=service.detail(5L,true).getInformation();
        assertTrue(detail.getCanReadSensitive()); assertEquals("sensitive-value",detail.getValues().get("id_card"));
        var image=new StudentInfoImageDO(); image.setId(100L); image.setFormId(40L);
        when(imageMapper.selectById(100L)).thenReturn(image);
        when(images.archiveRead(40L,100L)).thenReturn(new byte[]{1,2});
        assertArrayEquals(new byte[]{1,2},service.image(5L,100L));
        image.setFormId(41L);
        assertThrows(RuntimeException.class, () -> service.image(5L,100L));
        verify(images,times(1)).archiveRead(40L,100L);
    }
    @Test void archiveExportDoesNotInheritRegistrationExportOrRevealSensitiveValues() {
        when(permissions.hasAnyPermissions(any(), eq("zsjos:student-info-form:export"))).thenReturn(true);
        assertFalse(service.detail(5L,false).getInformation().getCanExport());
        assertThrows(RuntimeException.class, () -> service.export(5L));
        when(permissions.hasAnyPermissions(any(), eq("zsjos:student-archive:export"))).thenReturn(true);
        var value=new StudentInfoFormValueDO(); value.setFieldKey("mobile"); value.setSensitive(true); value.setValueText("13800000000");
        when(answers.byForm(40L)).thenReturn(List.of(value));
        var masked=service.export(5L).getInformation();
        assertTrue(masked.getCanExport()); assertEquals("138****0000",masked.getValues().get("mobile"));
        when(permissions.hasAnyPermissions(any(), eq("zsjos:student-archive:sensitive-read"))).thenReturn(true);
        assertEquals("13800000000",service.export(5L).getInformation().getValues().get("mobile"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings={"correct"})
    void optionalReasonPreservesRevisionAndOriginalRequestFingerprint(String reason) {
        var request=request(0); request.setReason(reason);
        when(collection.definitions(form)).thenReturn(List.of(field("job","text")));
        var job=new StudentInfoFormValueDO();job.setFieldKey("job");job.setValueText("teacher");
        when(fields.validateValues(anyList(),anyMap())).thenReturn(List.of(job));
        String fingerprint=cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(request));

        assertEquals(1,service.save(5L,request));

        var capture=ArgumentCaptor.forClass(StudentArchiveRevisionDO.class);verify(revisions).insert(capture.capture());
        assertEquals(Objects.requireNonNullElse(reason,""),capture.getValue().getReason());
        assertEquals(fingerprint,capture.getValue().getRequestHash());
        assertEquals(reason,request.getReason());
        when(revisions.byKey(request.getIdempotencyKey())).thenReturn(capture.getValue());
        assertEquals(1,service.save(5L,request));
        verify(revisions,times(1)).insert(any(StudentArchiveRevisionDO.class));
        verify(answers,never()).updateById(any(StudentInfoFormValueDO.class));
    }
    @Test void repeatedCommandReturnsOriginalRevision(){var req=request(0);var previous=new StudentArchiveRevisionDO();previous.setFormId(40L);previous.setRevision(1);previous.setRequestHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(req)));when(revisions.byKey("command")).thenReturn(previous);assertEquals(1,service.save(5L,req));verify(revisions,never()).insert(any(StudentArchiveRevisionDO.class));}
    @Test void imageFromOtherFormRejected(){when(collection.definitions(form)).thenReturn(List.of(field("portrait","images")));when(permissions.hasAnyPermissions(any(),any())).thenReturn(true);var image=new StudentInfoImageDO();image.setId(100L);image.setFormId(99L);image.setFieldKey("portrait");when(imageMapper.selectById(100L)).thenReturn(image);var req=request(0);req.setChanges(Map.of("portrait",List.of(100L)));assertThrows(RuntimeException.class,()->service.save(5L,req));verify(revisions,never()).insert(any(StudentArchiveRevisionDO.class));}
}
