package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionMemberMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionBindingMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormConfigMapper;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

class ExamCollectionTaskServiceTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void createSnapshotsSelectionAndReturnsOnlyExplicitResponseFields() {
        TenantContextHolder.setTenantId(1L);
        var service = new ExamCollectionTaskService();
        var tasks = mock(ExamCollectionTaskMapper.class); var configs = mock(StudentInfoFormConfigMapper.class);
        var permissions = mock(PermissionApi.class); var tokens = new ExamCollectionTokens();
        ReflectionTestUtils.setField(tokens, "key", "isolated-test-key");
        ReflectionTestUtils.setField(service, "tasks", tasks); ReflectionTestUtils.setField(service, "configs", configs);
        ReflectionTestUtils.setField(service, "permissions", permissions); ReflectionTestUtils.setField(service, "tokens", tokens);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://example.invalid/h5/");
        var config = new StudentInfoFormConfigDO(); config.setId(3L);
        config.setFieldsJson(JsonUtils.toJsonString(new StudentInfoFields().presets()));
        when(configs.state("PUBLISHED")).thenReturn(config);
        var request = new ExamCollectionVO.Create(); request.setIdempotencyKey("synthetic-create-key"); request.setTitle(" test task "); request.setSelectedFields(List.of("portrait"));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> service.create(request));
            verifyNoInteractions(tasks, configs);
            when(permissions.hasAnyPermissions(7L, QUERY)).thenReturn(true);
            when(permissions.hasAnyPermissions(7L, CREATE)).thenReturn(true);
            doAnswer(call -> { ((ExamCollectionTaskDO) call.getArgument(0)).setId(4L); return 1; }).when(tasks).insert(any(ExamCollectionTaskDO.class));
            request.setContent("<p style=\"text-align:center\"><strong>Instructions</strong></p><script>bad()</script>");
            var result = service.create(request);
            assertEquals("<p style=\"text-align:center\"><strong>Instructions</strong></p>", result.getContent());
            assertEquals(4L, result.getId()); assertEquals("test task", result.getTitle());
            assertEquals(4, result.getFields().size()); assertTrue(result.getUrl().startsWith("https://example.invalid/h5/exam-collection#token="));
            var row = ArgumentCaptor.forClass(ExamCollectionTaskDO.class); verify(tasks).insert(row.capture());
            assertEquals(result.getContent(), row.getValue().getContent());
            assertEquals(1L, row.getValue().getTenantId()); assertEquals(3L, row.getValue().getConfigVersionId());
            assertEquals(tokens.hash(row.getValue().getTokenCiphertext()), row.getValue().getTokenHash());
            var json = JsonUtils.toJsonString(result);
            assertFalse(json.contains("tokenHash")); assertFalse(json.contains("tokenCiphertext"));
            clearInvocations(tasks);
            for (String base : List.of("", "file:///tmp", "https://user@example.invalid", "https://example.invalid/?x=1")) {
                ReflectionTestUtils.setField(service, "publicBaseUrl", base);
                assertThrows(ServiceException.class, () -> service.create(request));
            }
            verify(tasks, never()).insert(any(ExamCollectionTaskDO.class));
            ReflectionTestUtils.setField(service, "publicBaseUrl", "https://example.invalid/h5/");
            ReflectionTestUtils.setField(service, "members", mock(ExamCollectionMemberMapper.class));
            ReflectionTestUtils.setField(service, "submissions", mock(ExamCollectionSubmissionMapper.class));
            ReflectionTestUtils.setField(service, "bindings", mock(ExamCollectionBindingMapper.class));
            when(tasks.byCreateRequest(anyString(), eq(false))).thenReturn(row.getValue());
            clearInvocations(configs);
            var replay = service.create(request);
            assertEquals(result.getId(), replay.getId());assertEquals(result.getUrl(), replay.getUrl());
            verifyNoInteractions(configs);
            request.setTitle("changed task");assertThrows(ServiceException.class,()->service.create(request));request.setTitle("test task");
            TenantContextHolder.setTenantId(2L);assertThrows(ServiceException.class,()->service.create(request));TenantContextHolder.setTenantId(1L);
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(8L);
            when(permissions.hasAnyPermissions(8L, QUERY)).thenReturn(true);when(permissions.hasAnyPermissions(8L, CREATE)).thenReturn(true);
            assertThrows(ServiceException.class,()->service.create(request));
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            // Simulate another transaction committing after our first read. Replay must use a current read.
            when(tasks.byCreateRequest(anyString(), eq(false))).thenReturn(null);
            when(tasks.byCreateRequest(anyString(), eq(true))).thenReturn(row.getValue());
            doThrow(new org.springframework.dao.DuplicateKeyException("concurrent synthetic request")).when(tasks).insert(any(ExamCollectionTaskDO.class));
            assertEquals(result.getUrl(), service.create(request).getUrl());
            verify(tasks).byCreateRequest(eq(row.getValue().getCreateRequestKey()), eq(true));
            when(tasks.byCreateRequest(anyString(), eq(true))).thenReturn(null);
            assertThrows(org.springframework.dao.DuplicateKeyException.class,()->service.create(request));
        }
    }
}
