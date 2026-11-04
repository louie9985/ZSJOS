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
        var request = new ExamCollectionVO.Create(); request.setTitle(" test task "); request.setSelectedFields(List.of("portrait"));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> service.create(request));
            verifyNoInteractions(tasks, configs);
            when(permissions.hasAnyPermissions(7L, QUERY)).thenReturn(true);
            when(permissions.hasAnyPermissions(7L, CREATE)).thenReturn(true);
            doAnswer(call -> { ((ExamCollectionTaskDO) call.getArgument(0)).setId(4L); return 1; }).when(tasks).insert(any(ExamCollectionTaskDO.class));
            var result = service.create(request);
            assertEquals(4L, result.getId()); assertEquals("test task", result.getTitle());
            assertEquals(5, result.getFields().size()); assertTrue(result.getUrl().startsWith("https://example.invalid/h5/exam-collection#token="));
            var row = ArgumentCaptor.forClass(ExamCollectionTaskDO.class); verify(tasks).insert(row.capture());
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
        }
    }
}
