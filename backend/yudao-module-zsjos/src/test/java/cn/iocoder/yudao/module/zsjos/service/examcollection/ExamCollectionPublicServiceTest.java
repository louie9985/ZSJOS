package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.pub.examcollection.vo.ExamCollectionPublicVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

class ExamCollectionPublicServiceTest {
    ExamCollectionPublicService service;
    ExamCollectionTaskMapper tasks;
    ExamCollectionSubmissionMapper submissions;
    ExamCollectionMatchingService matching;
    ExamCollectionAttachmentMapper attachments;
    ExamCollectionTaskDO task;
    ExamCollectionSubmissionDO session;
    ExamCollectionTokens tokens;
    String taskToken, sessionToken;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(77L); TenantContextHolder.setIgnore(true);
        service = new ExamCollectionPublicService(); tasks = inject("tasks", ExamCollectionTaskMapper.class);
        submissions = inject("submissions", ExamCollectionSubmissionMapper.class);
        matching = inject("matching", ExamCollectionMatchingService.class);
        attachments = inject("attachments", ExamCollectionAttachmentMapper.class);
        inject("tenants", TenantFrameworkService.class);
        tokens = new ExamCollectionTokens(); ReflectionTestUtils.setField(tokens, "key", "test-key");
        ReflectionTestUtils.setField(service, "tokens", tokens); ReflectionTestUtils.setField(service, "fields", new StudentInfoFields());
        taskToken = tokens.create(); sessionToken = tokens.create();
        task = new ExamCollectionTaskDO(); task.setId(1L); task.setTenantId(1L); task.setTokenHash(tokens.hash(taskToken));
        task.setStatus(OPEN); task.setSequenceNo(0L); task.setFieldsSnapshot(JsonUtils.toJsonString(ExamCollectionFields.snapshot(new StudentInfoFields().presets(), List.of())));
        session = new ExamCollectionSubmissionDO(); session.setId(2L); session.setTaskId(1L); session.setTenantId(1L);
        session.setSessionTokenHash(tokens.hash(sessionToken)); session.setStatus(DRAFT); session.setRevision(0);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        when(tasks.byToken(tokens.hash(taskToken))).thenReturn(task); when(tasks.lock(1L)).thenReturn(task); when(tasks.selectById(1L)).thenReturn(task);
        when(submissions.bySession(tokens.hash(sessionToken))).thenReturn(session); when(submissions.lock(2L)).thenReturn(session);
    }
    <T> T inject(String field, Class<T> type) { T value = mock(type); ReflectionTestUtils.setField(service, field, value); return value; }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    ExamCollectionPublicVO.Submit request() {
        var result = new ExamCollectionPublicVO.Submit(); result.setIdempotencyKey("request-one");
        // Publicly documented synthetic checksum example; no real person fixture.
        result.setValues(Map.of("name", "测试学员", "mobile", "13800000000", "wechat", "synthetic", "id_card", "11010519491231002X"));
        return result;
    }
    @Test void validSubmitUsesOwningTenantAndNetworkRetryDoesNotReapply() {
        doAnswer(call -> {
            assertEquals(1L, TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
            return null;
        }).when(matching).match(task, session);
        service.submit(taskToken, sessionToken, request());
        assertEquals(SUBMITTED, session.getStatus()); assertEquals(1L, task.getSequenceNo());
        assertEquals(tokens.identityHash("11010519491231002X"), session.getIdentityHash());
        service.submit(taskToken, sessionToken, request());
        verify(matching, times(1)).match(task, session);
        assertEquals(77L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
        var changed = request(); changed.setValues(new HashMap<>(changed.getValues())); changed.getValues().put("name", "另一次填写");
        assertThrows(ServiceException.class, () -> service.submit(taskToken, sessionToken, changed));
    }
    @Test void closedTaskDoesNotExposeFieldsOrAcceptNewSessionsAndSubmissions() {
        task.setStatus(COMPLETED);
        assertTrue(service.detail(taskToken).getFields().isEmpty());
        assertThrows(ServiceException.class, () -> service.start(taskToken));
        assertThrows(ServiceException.class, () -> service.submit(taskToken, sessionToken, request()));
        verifyNoInteractions(matching);
        verify(submissions, never()).insert(any(ExamCollectionSubmissionDO.class));
    }
    @Test void rejectsCrossTaskExpiredAndUnselectedFieldSubmissions() {
        session.setTaskId(99L);
        assertThrows(ServiceException.class, () -> service.submit(taskToken, sessionToken, request()));
        session.setTaskId(1L); session.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThrows(ServiceException.class, () -> service.submit(taskToken, sessionToken, request()));
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        var request = request(); request.setValues(new HashMap<>(request.getValues())); request.getValues().put("gift_shipping_address", "not collected");
        assertThrows(ServiceException.class, () -> service.submit(taskToken, sessionToken, request));
        verifyNoInteractions(matching);
        assertEquals(77L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
    }
    @Test void selectedAttachmentMustBelongToExactSession() {
        var definitions = ExamCollectionFields.snapshot(new StudentInfoFields().presets(), List.of("portrait"));
        task.setFieldsSnapshot(JsonUtils.toJsonString(definitions));
        var request = request(); request.setValues(new HashMap<>(request.getValues())); request.getValues().put("portrait", List.of(5L));
        var file = new ExamCollectionAttachmentDO(); file.setId(5L); file.setTaskId(1L); file.setTenantId(1L);
        file.setSubmissionId(99L); file.setFieldKey("portrait"); when(attachments.selectById(5L)).thenReturn(file);
        assertThrows(ServiceException.class, () -> service.submit(taskToken, sessionToken, request));
        verifyNoInteractions(matching);
    }
}
