package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderVoucherCompatibilityTest {
    @InjectMocks private SalesOrderServiceImpl service;
    @Mock private FileApi fileApi;

    @Test
    void prefixesLegacyMediaPathsAndPreservesAbsoluteUrls() {
        var result = convert(JsonUtils.toJsonString(List.of(
                "/media/uploads/凭证.jpg?download=1",
                "https://files.example.test/legacy-crm/voucher.jpg",
                "https://crm.zhongshijian.top/media/existing.png",
                "http://files.example.test/voucher.png")));

        assertEquals(List.of(
                "https://crm.zhongshijian.top/media/uploads/凭证.jpg?download=1",
                "https://files.example.test/legacy-crm/voucher.jpg",
                "https://crm.zhongshijian.top/media/existing.png",
                "http://files.example.test/voucher.png"),
                result.stream().map(SalesOrderRespVO.AttachmentVO::getFileUrl).toList());
        assertTrue(result.stream().allMatch(file -> file.getInfraFileId() == null));
        assertNull(result.getFirst().getFileSize());
        verifyNoInteractions(fileApi);
    }

    @Test
    void preservesMixedOrderAndSignsOnlyDistinctFileIds() {
        when(fileApi.presignGetUrls(List.of(7L), 600)).thenReturn(Map.of(7L, "https://files.example.test/signed"));
        var stored = Map.of("infraFileId", 7L, "fileUrl", "/media/private.pdf",
                "originalName", "voucher.pdf", "contentType", "application/pdf", "fileSize", 128L, "sort", 0);
        var result = convert(JsonUtils.toJsonString(List.of("/media/first.jpg", stored,
                Map.of("fileUrl", "/media/last.png", "originalName", "last.png"), stored)));

        assertEquals(4, result.size());
        assertEquals("https://crm.zhongshijian.top/media/first.jpg", result.get(0).getFileUrl());
        assertEquals("https://files.example.test/signed", result.get(1).getFileUrl());
        assertEquals(7L, result.get(1).getInfraFileId());
        assertEquals("voucher.pdf", result.get(1).getOriginalName());
        assertEquals("application/pdf", result.get(1).getContentType());
        assertEquals(128L, result.get(1).getFileSize());
        assertEquals("https://crm.zhongshijian.top/media/last.png", result.get(2).getFileUrl());
        assertNull(result.get(2).getInfraFileId());
        assertEquals(result.get(1).getFileUrl(), result.get(3).getFileUrl());
        verify(fileApi).presignGetUrls(List.of(7L), 600);
    }

    @Test
    void signingFailureKeepsMetadataWithoutExposingStoredPrivateUrl() {
        when(fileApi.presignGetUrls(List.of(7L), 600)).thenThrow(new IllegalStateException("unavailable"));
        var result = convert("[\"/media/legacy.jpg\",{\"infraFileId\":7,\"fileUrl\":\"/media/private.jpg\",\"originalName\":\"private.jpg\"}]");
        assertEquals("https://crm.zhongshijian.top/media/legacy.jpg", result.get(0).getFileUrl());
        assertNull(result.get(1).getFileUrl());
        assertEquals("private.jpg", result.get(1).getOriginalName());
    }

    @Test
    void absentSignedUrlsKeepAttachmentsUnavailable() {
        when(fileApi.presignGetUrls(List.of(7L), 600)).thenReturn(null, Map.of());
        String json = "[{\"infraFileId\":7,\"fileUrl\":\"/media/private.jpg\"}]";
        assertNull(convert(json).getFirst().getFileUrl());
        assertNull(convert(json).getFirst().getFileUrl());
    }

    @Test
    void emptyReferencesDoNotCallFileService() {
        for (String json : new String[] {null, "", "  ", "[]", "null"}) {
            assertTrue(convert(json).isEmpty());
        }
        verifyNoInteractions(fileApi);
    }

    @Test
    void malformedReferencesAreNotSilentlyDiscarded() {
        for (String json : List.of("{broken", "[42]", "[true]", "[null]")) {
            assertThrows(RuntimeException.class, () -> convert(json));
        }
        verifyNoInteractions(fileApi);
    }

    private List<SalesOrderRespVO.AttachmentVO> convert(String json) {
        return ReflectionTestUtils.invokeMethod(service, "convertVouchers", json);
    }
}
