package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadAppealSummaryServiceTest {
    @InjectMocks private LeadAppealSummaryService service;
    @Mock private LeadAppealMapper mapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper eventMapper;
    @Mock private LeadAppealService appeals;
    private LeadAppealDO appeal(long id, int round, String status) {
        var a = new LeadAppealDO(); a.setId(id); a.setLeadId(1L); a.setRoundNo(round); a.setStatus(status); return a;
    }
    private LeadManagementRespVO row(long id) {
        var r = new LeadManagementRespVO(); r.setId(id); return r;
    }
    @Test void batchesAuthorizedRowsAndChoosesLatestRoundWithoutTaskQueries() {
        var one = row(1); var two = row(2);
        when(mapper.selectListByLeadIds(List.of(1L, 2L))).thenReturn(List.of(
                appeal(12, 2, "quality_reviewing"), appeal(11, 1, "upheld")));
        service.populate(List.of(one, two), 9L, false);
        assertEquals("quality_reviewing", one.getAppealSummary().getStatus());
        assertFalse(one.getAppealSummary().isCanHandle()); assertNull(two.getAppealSummary());
        verifyNoInteractions(appeals);
    }
    @Test void detailUsesTaskAuthorizationAndReflectsCompletion() {
        var one = row(1);
        when(mapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of(appeal(11, 1, "sales_manager_reviewing")), List.of(appeal(11, 1, "overturned")));
        when(appeals.canHandleAppeal(11L, 9L)).thenReturn(true, false);
        service.populate(List.of(one), 9L, true); assertTrue(one.getAppealSummary().isCanHandle());
        service.populate(List.of(one), 9L, true); assertFalse(one.getAppealSummary().isCanHandle());
        assertEquals("overturned", one.getAppealSummary().getStatus());
    }
    @Test void oldCycleSummaryDoesNotLookLikeAnActiveAppealForNewDecision() {
        var old = appeal(11, 3, "overturned"); old.setInvalidEventId(10L);
        when(mapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of(old));
        when(eventMapper.selectLatestInvalidIds(List.of(1L))).thenReturn(java.util.Map.of(1L, 20L));
        var row = row(1); service.populate(List.of(row), 9L, true);
        assertNull(row.getAppealSummary()); verifyNoInteractions(appeals);
    }
    @Test void emptyPageDoesNotQuery() {
        service.populate(List.of(), 9L, false); verifyNoInteractions(mapper, appeals);
    }
}
