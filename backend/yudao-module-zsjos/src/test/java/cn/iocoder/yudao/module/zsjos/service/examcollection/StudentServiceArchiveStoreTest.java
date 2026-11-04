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
}
