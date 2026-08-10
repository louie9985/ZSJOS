package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadAttachmentServiceImplTest {

    @InjectMocks
    private LeadAttachmentServiceImpl service;
    @Mock
    private FileApi fileApi;

    @Test
    void uploadReturnsStableInfraFileReference() throws Exception {
        byte[] content = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
        MockMultipartFile file = new MockMultipartFile("file", "image.jpg", "image/jpeg", content);
        FileInfoRespDTO info = new FileInfoRespDTO(42L, 24L, "image.jpg", "zsjos/lead/image.jpg",
                "https://signed.test/image", "image/jpeg", (long) content.length, "10");
        when(fileApi.createFileInfo(any(), eq("image.jpg"), eq("zsjos/lead"), eq("image/jpeg")))
                .thenReturn(info);

        LeadAttachmentUploadRespVO result = service.upload(file);

        assertEquals(42L, result.getInfraFileId());
        assertEquals("https://signed.test/image", result.getFileUrl());
    }

    @Test
    void validateReferencesRejectsFileUploadedByAnotherUser() {
        LeadAttachmentReqVO reference = new LeadAttachmentReqVO();
        reference.setInfraFileId(42L);
        FileInfoRespDTO info = new FileInfoRespDTO(42L, 24L, "image.jpg", "zsjos/lead/image.jpg",
                null, "image/jpeg", 100L, "99");
        when(fileApi.getFileInfo(42L)).thenReturn(info);

        assertThrows(ServiceException.class, () -> service.validateReferences(java.util.List.of(reference), 10L));
    }

}
