package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.SalesDispatchPreferenceDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SalesDispatchPreferenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDispatchStatusServiceImplTest {

    @InjectMocks private SalesDispatchStatusServiceImpl service;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private SalesDispatchPreferenceMapper preferenceMapper;
    @Mock private LeadDispatchRedisRepository redisRepository;

    @Test
    void heartbeatDefaultsEligibleSalesToPausedAndMarksPresenceOnline() {
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));
        when(preferenceMapper.selectByUserId(10L)).thenReturn(null);
        when(redisRepository.isOnline(10L)).thenReturn(true);

        var result = service.heartbeat(10L);

        assertEquals("online", result.getPresence());
        assertEquals("paused", result.getMode());
        assertEquals("busy", result.getEffectiveStatus());
        verify(redisRepository).heartbeat(10L, false);
    }

    @Test
    void ineligibleUserIsRemovedAndNeverAppearsActive() {
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of());

        var result = service.heartbeat(10L);

        assertFalse(result.getEligible());
        assertEquals("offline", result.getEffectiveStatus());
        verify(redisRepository).offline(10L);
    }

    @Test
    void savedAcceptingPreferenceIsRestoredOnHeartbeat() {
        SalesDispatchPreferenceDO preference = new SalesDispatchPreferenceDO();
        preference.setUserId(10L);
        preference.setAcceptingEnabled(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));
        when(preferenceMapper.selectByUserId(10L)).thenReturn(preference);
        when(redisRepository.isOnline(10L)).thenReturn(true);

        var result = service.heartbeat(10L);

        assertEquals("accepting", result.getMode());
        assertEquals("online", result.getEffectiveStatus());
        verify(redisRepository).heartbeat(10L, true);
    }

    private static LeadAssignmentUserRespVO salesUser(Long id) {
        LeadAssignmentUserRespVO user = new LeadAssignmentUserRespVO();
        user.setId(id);
        return user;
    }
}
