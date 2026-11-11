package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFormService;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentServiceArchiveStoreTest {
    StudentServiceArchiveStore store;
    ServiceRelationMapper relations;
    StudentServiceArchiveRevisionMapper revisions;
    StudentInfoFormMapper forms;
    StudentArchiveRevisionMapper legacyRevisions;
    StudentInfoFormValueMapper answers;
    StudentInfoImageMapper images;
    StudentInfoFormService collection;
    ExamCollectionAttachmentMapper files;
    StudentInfoFields fieldValidation;
    ServiceRelationDO relation;
    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        store = new StudentServiceArchiveStore();
        relations = inject("relations", ServiceRelationMapper.class);
        revisions = inject("revisions", StudentServiceArchiveRevisionMapper.class);
        forms = inject("forms", StudentInfoFormMapper.class);
        legacyRevisions = inject("legacyRevisions", StudentArchiveRevisionMapper.class);
        answers = inject("answers", StudentInfoFormValueMapper.class);
        images = inject("legacyImages", StudentInfoImageMapper.class);
        collection = inject("collection", StudentInfoFormService.class);
        files = inject("attachments", ExamCollectionAttachmentMapper.class);
        fieldValidation = inject("fieldValidation", StudentInfoFields.class);
        relation = new ServiceRelationDO(); relation.setId(10L); relation.setPersonId(20L); relation.setTenantId(1L);
        when(relations.selectById(10L)).thenReturn(relation);
        when(relations.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    <T> T inject(String field, Class<T> type) { T value = mock(type); ReflectionTestUtils.setField(store, field, value); return value; }
    Field field(String key) { var value = new Field(); value.setKey(key); value.setType("text"); value.setRequired(true); return value; }
    StudentInfoFormValueDO answer(String key, String text) {
        var value = new StudentInfoFormValueDO(); value.setFieldKey(key); value.setFieldType("text"); value.setValueText(text); return value;
    }
    ExamCollectionTaskDO task() {
        var task = new ExamCollectionTaskDO(); task.setId(1L); task.setTenantId(1L);
        task.setFieldsSnapshot(JsonUtils.toJsonString(List.of(field("name")))); return task;
    }
    ExamCollectionSubmissionDO submission() {
        var row = new ExamCollectionSubmissionDO(); row.setId(2L); row.setTaskId(1L); row.setTenantId(1L);
        row.setSubmittedAt(LocalDateTime.now()); row.setRequestHash("hash");
        row.setAnswersJson(JsonUtils.toJsonString(List.of(answer("name", "submitted")))); row.setAttachmentIdsJson("[]"); return row;
    }
    @Test void historicalStudentNeedsNoSyntheticOrderOrForm() {
        assertEquals(1, store.apply(task(), submission(), 10L, null, ""));
        var captor = ArgumentCaptor.forClass(StudentServiceArchiveRevisionDO.class);
        verify(revisions).insert(captor.capture());
        assertEquals(20L, captor.getValue().getPersonId());
        assertEquals("submitted", JsonUtils.parseArray(captor.getValue().getAnswersJson(), StudentInfoFormValueDO.class).getFirst().getValueText());
        verifyNoInteractions(forms, answers, legacyRevisions, images);
    }
    @Test void legacyWechatSubmissionDoesNotReplaceExistingArchiveOrMutateRawHistory() {
        var current = revision(1,"staff_edit","[\"wechat\"]",List.of(answer("name","old"),answer("wechat","preserved")));
        when(revisions.latest(10L)).thenReturn(current);
        var task = task(); task.setFieldsSnapshot(JsonUtils.toJsonString(List.of(field("name"),field("wechat"))));
        var input = submission(); input.setAnswersJson(JsonUtils.toJsonString(List.of(answer("name","new"),answer("wechat","ignored"))));
        store.apply(task,input,10L,null,"");
        var written = ArgumentCaptor.forClass(StudentServiceArchiveRevisionDO.class); verify(revisions).insert(written.capture());
        assertTrue(written.getValue().getAnswersJson().contains("preserved"));
        assertFalse(written.getValue().getAnswersJson().contains("ignored"));
        assertTrue(input.getAnswersJson().contains("ignored"));
        assertFalse(written.getValue().getWrittenKeysJson().contains("wechat"));
    }
    @Test void mergesLatestRevisionWithoutOverwritingUncollectedValuesOrOldFiles() {
        var current = new StudentServiceArchiveRevisionDO(); current.setPersonId(20L); current.setRevision(3);
        current.setFieldsSnapshot(JsonUtils.toJsonString(List.of(field("name"), field("mobile"))));
        current.setAnswersJson(JsonUtils.toJsonString(List.of(answer("name", "old"), answer("mobile", "preserve"))));
        current.setAttachmentsJson(JsonUtils.toJsonString(List.of(new StudentServiceArchiveStore.Attachment("registration", 7L, "portrait", "", "photo.png", "image/png", 10L))));
        when(revisions.latest(10L)).thenReturn(current);
        assertEquals(4, store.apply(task(), submission(), 10L, 30L, "manual resolution"));
        var captor = ArgumentCaptor.forClass(StudentServiceArchiveRevisionDO.class); verify(revisions).insert(captor.capture());
        var written = captor.getValue();
        assertTrue(written.getAnswersJson().contains("preserve"));
        assertTrue(written.getAttachmentsJson().contains("photo.png"));
        assertTrue(current.getAnswersJson().contains("old"));
        assertEquals(30L, written.getOperatorUserId());
    }
    @Test void includesLegacyArchiveOnlyFilesSelectedByRevision() {
        relation.setRegistrationCaseId(5L); relation.setOrderItemId(6L);
        var form = new StudentInfoFormDO(); form.setId(1L); form.setPersonId(20L); form.setOrderItemId(6L); form.setStatus("SUBMITTED");
        when(forms.byCase(5L)).thenReturn(form);
        var revision = new StudentArchiveRevisionDO(); revision.setAnswersJson("[]"); revision.setImageIdsJson("[7]");
        when(legacyRevisions.latest(1L)).thenReturn(revision);
        var image = new StudentInfoImageDO(); image.setId(7L); image.setFormId(1L); image.setArchiveOnly(true); image.setFieldKey("portrait");
        when(images.allByForm(1L)).thenReturn(List.of(image)); when(collection.definitions(form)).thenReturn(List.of());
        assertEquals(7L, store.current(10L).attachments().getFirst().id());
    }
    @Test void sameSubmissionReplaysOnlyMatchingFingerprint() {
        var previous = new StudentServiceArchiveRevisionDO(); previous.setRevision(4); previous.setRequestHash("hash");
        when(revisions.byRequest(10L, "exam-submit:2")).thenReturn(previous);
        assertEquals(4, store.apply(task(), submission(), 10L, null, ""));
        previous.setRequestHash("different");
        assertThrows(ServiceException.class, () -> store.apply(task(), submission(), 10L, null, ""));
        verify(revisions, never()).insert(any(StudentServiceArchiveRevisionDO.class));
    }
    @Test void rejectsCrossTenantAndOtherSubmissionAttachmentsBeforeWriting() {
        var input = submission(); input.setTenantId(2L);
        assertThrows(ServiceException.class, () -> store.apply(task(), input, 10L, null, ""));
        input.setTenantId(1L); input.setAttachmentIdsJson("[7]");
        var file = new ExamCollectionAttachmentDO(); file.setTenantId(1L); file.setTaskId(1L); file.setSubmissionId(99L);
        when(files.selectById(7L)).thenReturn(file);
        assertThrows(ServiceException.class, () -> store.apply(task(), input, 10L, null, ""));
        verify(revisions, never()).insert(any(StudentServiceArchiveRevisionDO.class));
    }
    @Test void selectedRequiredMaterialCannotSilentlyClearTheArchive() {
        var task = task(); var photo = field("portrait"); photo.setType("images"); photo.setMaxImages(9);
        task.setFieldsSnapshot(JsonUtils.toJsonString(List.of(field("name"), photo)));
        assertThrows(ServiceException.class, () -> store.apply(task, submission(), 10L, null, ""));
        var input = submission(); input.setAttachmentIdsJson("[7,7]");
        assertThrows(ServiceException.class, () -> store.apply(task, input, 10L, null, ""));
        verify(revisions, never()).insert(any(StudentServiceArchiveRevisionDO.class));
    }

    @Test void changedIdentityRefreshesExistingAgeButPreservesUnchangedGenderSnapshotAndUncollectedAddress() {
        var current = new StudentServiceArchiveRevisionDO(); current.setPersonId(20L); current.setRevision(1);
        var age = field("age"); age.setEnabled(true);
        var gender = field("gender"); gender.setEnabled(true); gender.setType("dict");
        var oldGender = answer("gender", null); oldGender.setValueCode("2"); oldGender.setValueLabelSnapshot("历史性别标签");
        current.setFieldsSnapshot(JsonUtils.toJsonString(List.of(field("id_card"), age, gender, field("household_area"))));
        current.setAnswersJson(JsonUtils.toJsonString(List.of(answer("id_card", "110105198001010026"), answer("age", "46"), oldGender, answer("household_area", "历史地址"))));
        current.setAttachmentsJson("[]"); when(revisions.latest(10L)).thenReturn(current);
        var task = task(); var id = field("id_card"); id.setEnabled(true); task.setFieldsSnapshot(JsonUtils.toJsonString(List.of(id)));
        var input = submission(); input.setAnswersJson(JsonUtils.toJsonString(List.of(answer("id_card", "11010519491231002X"))));
        when(fieldValidation.validateValues(anyList(), anyMap())).thenReturn(List.of(answer("age", "76")));
        store.apply(task, input, 10L, null, "");
        verify(fieldValidation).validateValues(argThat(list -> list.stream().map(Field::getKey).toList().equals(List.of("id_card", "age"))), eq(java.util.Map.of("id_card", "11010519491231002X")));
        var captor = ArgumentCaptor.forClass(StudentServiceArchiveRevisionDO.class); verify(revisions).insert(captor.capture());
        assertTrue(captor.getValue().getAnswersJson().contains("历史性别标签"));
        assertTrue(captor.getValue().getAnswersJson().contains("历史地址"));
        assertTrue(captor.getValue().getAnswersJson().contains("76"));
    }

    @Test void unchangedIdentityPreservesHistoricalDerivedSnapshots() {
        var current = new StudentServiceArchiveRevisionDO(); current.setPersonId(20L); current.setRevision(1);
        var age = field("age"); age.setEnabled(true);
        current.setFieldsSnapshot(JsonUtils.toJsonString(List.of(field("id_card"), age)));
        current.setAnswersJson(JsonUtils.toJsonString(List.of(answer("id_card", "11010519491231002x"), answer("age", "75"))));
        current.setAttachmentsJson("[]"); when(revisions.latest(10L)).thenReturn(current);
        var task = task(); task.setFieldsSnapshot(JsonUtils.toJsonString(List.of(field("id_card"))));
        var input = submission(); input.setAnswersJson(JsonUtils.toJsonString(List.of(answer("id_card", "11010519491231002X"))));
        store.apply(task, input, 10L, null, "");
        verifyNoInteractions(fieldValidation);
        var captor = ArgumentCaptor.forClass(StudentServiceArchiveRevisionDO.class); verify(revisions).insert(captor.capture());
        assertTrue(captor.getValue().getAnswersJson().contains("75"));
    }

    StudentServiceArchiveRevisionDO revision(int number, String source, String intent, List<StudentInfoFormValueDO> values) {
        var row = new StudentServiceArchiveRevisionDO(); row.setTenantId(1L); row.setServiceRelationId(10L); row.setPersonId(20L);
        row.setRevision(number); row.setTaskId(1L); row.setSubmissionId(2L); row.setSource(source); row.setWrittenKeysJson(intent);
        row.setFieldsSnapshot(JsonUtils.toJsonString(values.stream().map(v -> field(v.getFieldKey())).toList()));
        row.setAnswersJson(JsonUtils.toJsonString(values)); row.setAttachmentsJson("[]"); return row;
    }
    @Test void restorationAppendsRevisionAndRetainsLaterExplicitWriteWithoutEditingHistory() {
        var wrong = revision(1, "exam_submission", "[\"name\"]", List.of(answer("name", "wrong")));
        var later = revision(2, "staff_edit", "[\"mobile\"]", List.of(answer("name", "wrong"), answer("mobile", "later")));
        when(revisions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(wrong, later));
        var result = store.restore(task(), java.util.Set.of(2L), 10L, 2, 7L, "归属纠正", 3L, 4);
        assertEquals(java.util.Set.of("name"), result.restoredKeys());
        var captor = ArgumentCaptor.forClass(StudentServiceArchiveRevisionDO.class); verify(revisions).insert(captor.capture());
        assertEquals(3, captor.getValue().getRevision());assertEquals("exam_correction_restore", captor.getValue().getSource());
        assertFalse(captor.getValue().getAnswersJson().contains("wrong"));assertTrue(captor.getValue().getAnswersJson().contains("later"));
        assertTrue(wrong.getAnswersJson().contains("wrong"));verify(revisions, never()).updateById(any(StudentServiceArchiveRevisionDO.class));
    }
    @Test void restorationRejectsMissingLaterIntentOrStalePreview() {
        var wrong = revision(1, "exam_submission", "[\"name\"]", List.of(answer("name", "wrong")));
        var unknown = revision(2, "staff_edit", null, List.of(answer("name", "wrong")));
        when(revisions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(wrong, unknown));
        assertThrows(ServiceException.class, () -> store.restore(task(), java.util.Set.of(2L), 10L, 2, 7L, "纠正", 3L, 4));
        unknown.setWrittenKeysJson("[\"name\"]");
        assertThrows(ServiceException.class, () -> store.restore(task(), java.util.Set.of(2L), 10L, 1, 7L, "纠正", 3L, 4));
        verify(revisions, never()).insert(any(StudentServiceArchiveRevisionDO.class));
    }
}
