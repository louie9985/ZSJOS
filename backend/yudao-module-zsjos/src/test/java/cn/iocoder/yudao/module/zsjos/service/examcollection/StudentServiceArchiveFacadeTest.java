package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveVO.Save;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@SuppressWarnings("unchecked")
class StudentServiceArchiveFacadeTest {
    StudentServiceArchiveFacade facade;
    StudentServiceArchiveStore store;
    StudentServiceArchiveRevisionMapper revisions;
    ServiceRelationMapper relations;
    PermissionApi permissions;
    ExamCollectionAttachmentMapper exam;
    StudentServiceArchiveAttachmentMapper uploaded;
    FileApi files;
    ExamCollectionSubmissionMapper submissions;
    ExamCollectionTaskMapper tasks;
    Snapshot current;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); facade = new StudentServiceArchiveFacade();
        store = inject("store", StudentServiceArchiveStore.class); revisions = inject("revisions", StudentServiceArchiveRevisionMapper.class);
        relations = inject("relations", ServiceRelationMapper.class); permissions = inject("permissions", PermissionApi.class);
        exam = inject("examFiles", ExamCollectionAttachmentMapper.class); uploaded = inject("uploaded", StudentServiceArchiveAttachmentMapper.class); files = inject("files", FileApi.class);
        submissions = inject("submissions", ExamCollectionSubmissionMapper.class); tasks = inject("tasks", ExamCollectionTaskMapper.class);
        ReflectionTestUtils.setField(facade, "fields", new StudentInfoFields());
        var relation = new ServiceRelationDO(); relation.setId(10L); relation.setPersonId(20L); relation.setTenantId(1L);
        when(relations.selectById(10L)).thenReturn(relation); when(relations.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        var name = new Field(); name.setKey("name"); name.setLabel("姓名"); name.setType("text"); name.setRequired(true); name.setEnabled(true); name.setSensitive(false);
        var mobile = new Field(); mobile.setKey("mobile"); mobile.setLabel("手机号码"); mobile.setType("text"); mobile.setRequired(true); mobile.setEnabled(true); mobile.setSensitive(true);
        current = new Snapshot(10L, 20L, 3, List.of(name, mobile), List.of(answer("name", "原姓名", false), answer("mobile", "13800000000", true)),
                List.of(new Attachment("registration", 1L, "portrait", "", "old.png", "image/png", 10L), new Attachment("exam", 1L, "diploma_images", "", "new.png", "image/png", 20L)));
        when(store.current(10L)).thenReturn(current); when(store.baseline(10L)).thenReturn(new Snapshot(10L, 20L, 0, List.of(), List.of(), List.of()));
        when(revisions.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of());
    }
    <T> T inject(String field, Class<T> type) { T value = mock(type); ReflectionTestUtils.setField(facade, field, value); return value; }
    StudentInfoFormValueDO answer(String key, String text, boolean sensitive) {
        var row = new StudentInfoFormValueDO(); row.setFieldKey(key); row.setFieldType("text"); row.setValueText(text); row.setSensitive(sensitive); return row;
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void detailMasksSensitiveValuesAndKeepsCollidingAttachmentIdsDistinct() {
        var detail = facade.detail(10L, false);
        assertNotEquals("13800000000", detail.getInformation().getValues().get("mobile"));
        assertEquals(List.of("registration", "exam"), detail.getInformation().getImages().stream().map(i -> i.getSource()).toList());
        assertFalse(detail.getCanMaintain());
        assertThrows(ServiceException.class, () -> facade.detail(10L, true));
        when(permissions.hasAnyPermissions(null, ARCHIVE_SENSITIVE)).thenReturn(true);
        assertEquals("13800000000", facade.detail(10L, true).getInformation().getValues().get("mobile"));
    }
    @Test void staffEditAppendsRevisionPreservingOtherValuesAndAttachments() {
        var request = new Save(); request.setRevision(3); request.setIdempotencyKey("edit"); request.setChanges(Map.of("name", "新姓名"));
        assertEquals(4, facade.save(10L, request));
        var row = ArgumentCaptor.forClass(StudentServiceArchiveRevisionDO.class); verify(revisions).insert(row.capture());
        var written = row.getValue(); assertEquals("staff_edit", written.getSource());
        assertTrue(written.getAnswersJson().contains("新姓名")); assertTrue(written.getAnswersJson().contains("13800000000"));
        assertEquals(2, JsonUtils.parseArray(written.getAttachmentsJson(), Attachment.class).size());
        assertEquals("原姓名", current.answers().getFirst().getValueText());
        request.setRevision(2); assertThrows(ServiceException.class, () -> facade.save(10L, request));
    }
    @Test void sensitiveEditAndUnreferencedExamFileCannotBypassExistingBoundary() {
        var request = new Save(); request.setRevision(3); request.setIdempotencyKey("edit"); request.setChanges(Map.of("mobile", "13900000000"));
        assertThrows(ServiceException.class, () -> facade.save(10L, request));
        var row = new ExamCollectionAttachmentDO(); row.setId(8L); row.setTenantId(1L); when(exam.selectById(8L)).thenReturn(row);
        when(revisions.selectCount(any(LambdaQueryWrapperX.class))).thenReturn(0L);
        assertThrows(ServiceException.class, () -> facade.read(10L, 8L, "exam")); verifyNoInteractions(files);
        var upload = new StudentServiceArchiveAttachmentDO(); upload.setTenantId(1L); upload.setServiceRelationId(99L); when(uploaded.selectById(8L)).thenReturn(upload);
        assertThrows(ServiceException.class, () -> facade.read(10L, 8L, "service")); verifyNoInteractions(files);
    }
    @Test void originalSubmissionHistoryRetainsOlderResolvedAnswersSeparatelyFromCurrentArchive() {
        var old = new ExamCollectionSubmissionDO(); old.setId(5L); old.setTaskId(1L); old.setTenantId(1L); old.setServiceRelationId(10L);
        old.setSubmittedAt(java.time.LocalDateTime.of(2026, 11, 4, 10, 0)); old.setAttachmentIdsJson("[]");
        old.setAnswersJson(JsonUtils.toJsonString(List.of(answer("name", "第一次原始姓名", false))));
        when(submissions.selectById(5L)).thenReturn(old); when(submissions.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(old));
        var task = new ExamCollectionTaskDO(); task.setId(1L); task.setTitle("考试任务"); task.setFieldsSnapshot(JsonUtils.toJsonString(current.fields()));
        when(tasks.selectById(1L)).thenReturn(task); when(tasks.selectByIds(anyCollection())).thenReturn(List.of(task));
        var detail = facade.detail(10L, false);
        assertEquals("原姓名", detail.getInformation().getValues().get("name"));
        assertEquals(5L, detail.getSubmissions().getFirst().getSubmissionId()); assertEquals("考试任务", detail.getSubmissions().getFirst().getTaskTitle());
        var original = facade.submission(10L, 5L, false);
        assertEquals("第一次原始姓名", original.getInformation().getValues().get("name")); assertFalse(original.getCanMaintain());
        old.setServiceRelationId(99L); assertThrows(ServiceException.class, () -> facade.submission(10L, 5L, false));
    }
    @Test void existingCourseMetadataAndLegacyMaintenanceHistoryRemainVisible() {
        var relation = relations.selectById(10L); relation.setRegistrationCaseId(30L); relation.setOrderItemId(40L);
        var forms = inject("forms", cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormMapper.class);
        var configs = inject("configs", cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormConfigMapper.class);
        var legacy = inject("legacyRevisions", cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentArchiveRevisionMapper.class);
        var users = inject("users", cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        var form = new cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO();
        form.setId(1L); form.setPersonId(20L); form.setOrderItemId(40L); form.setConfigVersionId(5L); form.setProductName("原报名课程");
        when(forms.byCase(30L)).thenReturn(form);
        var config = new cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormConfigDO(); config.setVersionNo(8);
        when(configs.selectById(5L)).thenReturn(config);
        var old = new cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentArchiveRevisionDO(); old.setRevision(4); old.setOperatorUserId(7L); old.setReason("原维护记录");
        when(legacy.history(1L)).thenReturn(List.of(old));
        var operator = new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO(); operator.setNickname("测试考务");
        when(users.getUserMap(anyCollection())).thenReturn(Map.of(7L, operator));
        var result = facade.detail(10L, false);
        assertEquals("原报名课程", result.getProductName()); assertEquals(8, result.getInformation().getConfigVersion());
        assertTrue(result.getHistory().stream().anyMatch(h -> "registration".equals(h.getSource()) && "原维护记录".equals(h.getReason())));
    }
    @Test void delayedManualResolutionDisplaysOriginalSubmissionTimeNotArchiveWriteTime() {
        var revision=new StudentServiceArchiveRevisionDO();revision.setRevision(2);revision.setTaskId(1L);revision.setSubmissionId(5L);revision.setSource("exam_submission");
        revision.setCreateTime(java.time.LocalDateTime.of(2026,11,5,12,0));
        when(revisions.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(revision));
        var original=new ExamCollectionSubmissionDO();original.setId(5L);original.setTaskId(1L);original.setServiceRelationId(10L);original.setSubmittedAt(java.time.LocalDateTime.of(2026,11,4,9,0));
        when(submissions.selectById(5L)).thenReturn(original);
        assertEquals(original.getSubmittedAt(),facade.detail(10L,false).getInformation().getSubmittedAt());
        original.setServiceRelationId(99L);
        assertNotEquals(original.getSubmittedAt(),facade.detail(10L,false).getInformation().getSubmittedAt());
    }
}
