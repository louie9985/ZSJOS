package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionExportVO.Request;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionExportService;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionPermissionProvider;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExamCollectionExportTypeProviderTest {
    @Test void validatesAndGeneratesUsingOriginalPreviewThenDeletesTemporaryZip() throws Exception {
        var provider = new ExamCollectionExportTypeProvider();
        var exports = mock(ExamCollectionExportService.class);
        var permissions = mock(ExamCollectionPermissionProvider.class);
        ReflectionTestUtils.setField(provider, "exports", exports);
        ReflectionTestUtils.setField(provider, "permissions", permissions);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ReflectionTestUtils.setField(provider, "validator", factory.getValidator());
            var request = new Request(); request.setFingerprint("preview-1");
            String json = JsonUtils.toJsonString(new ExamCollectionExportTypeProvider.Filter(2L, request));
            provider.validateFilter(json);
            verify(permissions).check(eq(2L), eq("query"), any());
            var path = Files.createTempFile("exam-provider-test-", ".zip");
            Files.write(path, new byte[]{1,2,3});
            when(exports.download(eq(2L), any())).thenAnswer(call -> {
                assertEquals("preview-1", call.<Request>getArgument(1).getFingerprint());
                return path;
            });
            when(exports.downloadName(2L)).thenReturn("测试(202611111800).zip");
            var task = new ExportTaskDO().setFilterJson(json);
            var result = provider.generate(task);
            assertArrayEquals(new byte[]{1,2,3}, result.content());
            assertEquals("测试(202611111800).zip", result.fileName());
            assertFalse(Files.exists(path));
            assertTrue(provider.privateResult());
            request.setFingerprint(null);
            assertThrows(IllegalArgumentException.class, () -> provider.validateFilter(
                    JsonUtils.toJsonString(new ExamCollectionExportTypeProvider.Filter(2L, request))));
        }
    }
}
