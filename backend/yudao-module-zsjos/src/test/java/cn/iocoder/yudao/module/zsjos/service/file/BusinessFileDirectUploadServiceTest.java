package cn.iocoder.yudao.module.zsjos.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadCompleteReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadInitReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadInitRespDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_VERSION_FILE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessFileDirectUploadServiceTest {

    @InjectMocks private BusinessFileDirectUploadService service;
    @Mock private FileApi fileApi;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void initContentBindsTenantAdminUserSceneAndDirectory() {
        ZsjosDirectUploadInitReqVO request = uploadRequest("video/mp4", 1024L);
        when(fileApi.initDirectUpload(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FileDirectUploadInitRespDTO());

        service.initContent(request, 42L);

        ArgumentCaptor<FileDirectUploadInitReqDTO> captor = ArgumentCaptor.forClass(FileDirectUploadInitReqDTO.class);
        verify(fileApi).initDirectUpload(captor.capture());
        assertEquals(7L, captor.getValue().getTenantId());
        assertEquals(2, captor.getValue().getUserType());
        assertEquals(42L, captor.getValue().getUserId());
        assertEquals("ZSJOS_CONTENT", captor.getValue().getScene());
        assertEquals("zsjos/content/42", captor.getValue().getDirectory());
    }

    @Test
    void initContentRejectsNonMediaAndOverOneGigabyte() {
        assertServiceCode(() -> service.initContent(uploadRequest("application/pdf", 1024L), 42L));
        assertServiceCode(() -> service.initContent(uploadRequest("video/mp4", 1024L * 1024 * 1024 + 1), 42L));
    }

    @Test
    void completeContentRejectsWrongDirectoryReturnedByInfra() {
        when(fileApi.completeDirectUpload(org.mockito.ArgumentMatchers.any())).thenReturn(
                new FileInfoRespDTO(10L, 8L, "video.mp4", "zsjos/material/42/id/video.mp4",
                        "https://files.test/video.mp4", "video/mp4", 1024L, "42"));

        assertServiceCode(() -> service.completeContent("token", 42L));
    }

    @Test
    void completeMaterialUsesDifferentScene() {
        when(fileApi.completeDirectUpload(org.mockito.ArgumentMatchers.any())).thenReturn(
                new FileInfoRespDTO(10L, 8L, "file.pdf", "zsjos/material/42/id/file.pdf",
                        "https://files.test/file.pdf", "application/pdf", 1024L, "42"));

        service.completeMaterial("token", 42L);

        ArgumentCaptor<FileDirectUploadCompleteReqDTO> captor =
                ArgumentCaptor.forClass(FileDirectUploadCompleteReqDTO.class);
        verify(fileApi).completeDirectUpload(captor.capture());
        assertEquals("ZSJOS_MATERIAL", captor.getValue().getScene());
        assertEquals(7L, captor.getValue().getTenantId());
    }

    private ZsjosDirectUploadInitReqVO uploadRequest(String contentType, long size) {
        ZsjosDirectUploadInitReqVO request = new ZsjosDirectUploadInitReqVO();
        request.setName("video.mp4");
        request.setContentType(contentType);
        request.setSize(size);
        return request;
    }

    private void assertServiceCode(org.junit.jupiter.api.function.Executable executable) {
        ServiceException error = assertThrows(ServiceException.class, executable);
        assertEquals(CONTENT_VERSION_FILE_INVALID.getCode(), error.getCode());
    }
}
