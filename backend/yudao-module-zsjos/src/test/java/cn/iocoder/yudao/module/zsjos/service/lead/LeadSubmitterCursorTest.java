package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LeadSubmitterCursorTest {
    @Test void changingPartnerOrClearingFilterRejectsPreviousCursor() {
        var service = new LeadManagementServiceImpl();
        var req = new LeadManagementPageReqVO(); req.setPartnerSubmitterId(48L);
        var row = new LeadManagementRespVO(); row.setId(7L);
        row.setLastActivityAt(LocalDateTime.of(2026,10,8,10,0));
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
