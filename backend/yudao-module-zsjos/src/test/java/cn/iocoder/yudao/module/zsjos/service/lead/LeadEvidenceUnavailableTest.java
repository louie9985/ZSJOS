package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeadEvidenceUnavailableTest {
    private final FileApi fileApi = mock(FileApi.class);

    @Test void complaintWithoutFileIdRetainsMetadataButHasNoDownloadUrl() {
        var service = new LeadComplaintService();
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        List<LeadComplaintRespVO.EvidenceVO> result = ReflectionTestUtils.invokeMethod(service, "toEvidence",
                "[{\"infraFileId\":null,\"fileUrl\":\"https://unknown.test/file\",\"name\":\"证据\"}]");
        assertEquals(1, result.size());
        assertNull(result.get(0).getFileUrl());
        assertEquals("证据", result.get(0).getOriginalName());
        verifyNoInteractions(fileApi);
    }

    @Test void appealMissingOrUnresolvedFileIdNeverFallsBackToStoredUrl() {
        var service = new LeadAppealServiceImpl();
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        when(fileApi.presignGetUrls(anyCollection(), anyInt())).thenReturn(Map.of(8L, "signed-file"));
        List<LeadAppealRespVO.EvidenceVO> result = ReflectionTestUtils.invokeMethod(service, "toEvidenceVO",
                "[{\"infraFileId\":null,\"fileUrl\":\"https://unknown.test/a\",\"originalName\":\"旧证据\"},"
                        + "{\"infraFileId\":7,\"fileUrl\":\"https://unknown.test/b\"},"
                        + "{\"infraFileId\":8,\"fileUrl\":\"https://unknown.test/c\"}]");
        assertEquals(3, result.size());
        assertNull(result.get(0).getFileUrl());
        assertNull(result.get(1).getFileUrl());
        assertEquals("signed-file", result.get(2).getFileUrl());
        verify(fileApi).presignGetUrls(List.of(7L, 8L), 600);
    }

    @Test void appealWithoutAnyFileIdDoesNotAskInfraToSignEmptyIds() {
        var service = new LeadAppealServiceImpl();
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        List<LeadAppealRespVO.EvidenceVO> result = ReflectionTestUtils.invokeMethod(service, "toEvidenceVO",
                "[{\"fileUrl\":\"https://unknown.test/a\"}]");
        assertNull(result.get(0).getFileUrl());
        verifyNoInteractions(fileApi);
    }
}
