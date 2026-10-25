package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LeadSubmitterCursorTest {
    @Test void submissionCursorIsStableAcrossActivityAndRejectsOldProtocol() {
        var service = new LeadManagementServiceImpl(); var req = new LeadManagementPageReqVO();
        var row = new LeadManagementRespVO(); row.setId(7L);
        row.setSubmittedAt(LocalDateTime.of(2026,10,8,10,0));
        row.setLastActivityAt(LocalDateTime.of(2026,10,22,10,0));
        String token = ReflectionTestUtils.invokeMethod(service, "encodeCursor", row, req, 9L);
        String raw = new String(java.util.Base64.getUrlDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(raw.startsWith("2026-10-08T10:00|7|9|submitted-v1:"));
        row.setLastActivityAt(LocalDateTime.of(2026,10,23,10,0));
        assertEquals(token, ReflectionTestUtils.invokeMethod(service, "encodeCursor", row, req, 9L));
        String old = java.util.Base64.getUrlEncoder().encodeToString(raw.replace("submitted-v1:", "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service, "decodeCursor", old, req, 9L));
    }
    @Test void changingPartnerOrClearingFilterRejectsPreviousCursor() {
        var service = new LeadManagementServiceImpl();
        var req = new LeadManagementPageReqVO(); req.setPartnerSubmitterId(48L);
        var row = new LeadManagementRespVO(); row.setId(7L);
        row.setSubmittedAt(LocalDateTime.of(2026,10,8,10,0));
        String cursor = ReflectionTestUtils.invokeMethod(service, "encodeCursor", row, req, 9L);
        assertNotNull(ReflectionTestUtils.invokeMethod(service, "decodeCursor", cursor, req, 9L));
        req.setPartnerSubmitterId(50L);
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "decodeCursor", cursor, req, 9L));
        req.setPartnerSubmitterId(null);
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "decodeCursor", cursor, req, 9L));
    }
}
