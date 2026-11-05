package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOrderLeadTimeCompatibilityTest {
    private final LeadMapper mapper = mock(LeadMapper.class);
    private void apply(SalesOrderRespVO result) {
        var service = new SalesOrderServiceImpl();
        ReflectionTestUtils.setField(service, "leadMapper", mapper);
        ReflectionTestUtils.invokeMethod(service, "applyLeadSubmittedAtFallback", result);
    }
    private SalesOrderRespVO response() {
        var result = new SalesOrderRespVO();
        result.setLeadId(1L);
        result.setLeadProfile(new SalesOrderRespVO.LeadProfileVO());
        result.getLeadProfile().setSubmittedName("Historical name");
        return result;
    }
    @Test void supplementsOnlyMissingTimeFromOriginalLead() {
        var result = response();
        var lead = new LeadDO();
        var time = LocalDateTime.of(2026, 10, 1, 9, 30);
        lead.setCreateTime(time);
        lead.setSubmittedName("Current name");
        when(mapper.selectById(1L)).thenReturn(lead);
        apply(result);
        assertEquals(time, result.getLeadProfile().getSubmittedAt());
        assertEquals("Historical name", result.getLeadProfile().getSubmittedName());
        verify(mapper).selectById(1L);
        verifyNoMoreInteractions(mapper);
    }
    @Test void preservesSnapshotTimeWithoutQueryingLead() {
        var result = response();
        var time = LocalDateTime.of(2026, 9, 1, 9, 30);
        result.getLeadProfile().setSubmittedAt(time);
        apply(result);
        assertEquals(time, result.getLeadProfile().getSubmittedAt());
        verifyNoInteractions(mapper);
    }
    @Test void missingLeadRemainsUnknownAndUnlinkedProfilesAreNotInvented() {
        var result = response();
        apply(result);
        assertNull(result.getLeadProfile().getSubmittedAt());
        result.setLeadId(null);
        apply(result);
        result.setLeadProfile(null);
        apply(result);
        assertNull(result.getLeadProfile());
        verify(mapper, times(1)).selectById(1L);
        verifyNoMoreInteractions(mapper);
    }
}
