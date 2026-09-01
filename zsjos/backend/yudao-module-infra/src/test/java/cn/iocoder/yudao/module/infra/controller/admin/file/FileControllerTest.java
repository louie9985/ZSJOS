package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileControllerTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void uploadAvatarReturnsStableAdminApiUrl() throws Exception {
        FileService fileService = mock(FileService.class);
        when(fileService.createAvatarFile(any(byte[].class), eq("system/user/avatar")))
                .thenReturn(new FileDO().setId(42L));

        FileController controller = new FileController();
        ReflectionTestUtils.setField(controller, "fileService", fileService);
        ReflectionTestUtils.setField(controller, "webProperties", new WebProperties());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin-api/infra/file/avatar/upload");
        request.setScheme("https");
        request.setServerName("api.example.test");
        request.setServerPort(443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        FileUploadReqVO upload = new FileUploadReqVO();
        upload.setDirectory("system/user/avatar");
        upload.setFile(new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        CommonResult<String> result = controller.uploadAvatar(upload);

        assertEquals("https://api.example.test/admin-api/infra/file/avatar/42", result.getData());
    }
}
