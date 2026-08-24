package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.SalesDispatchPreferenceDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SalesDispatchPreferenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDispatchStatusServiceImplTest {

    @InjectMocks private SalesDispatchStatusServiceImpl service;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private SalesDispatchPreferenceMapper preferenceMapper;
    @Mock private LeadDispatchRedisRepository redisRepository;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

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

    @Test
    void managerCanPausePersistedPreferenceWithoutEligibilityCheck() {
        SalesDispatchPreferenceDO preference = new SalesDispatchPreferenceDO();
        preference.setId(7L);
        preference.setUserId(10L);
        preference.setAcceptingEnabled(true);
        when(preferenceMapper.selectByUserId(10L)).thenReturn(preference);

        assertTrue(service.pausePreferenceByManager(10L));

        assertFalse(preference.getAcceptingEnabled());
        verify(preferenceMapper).updateById(preference);
        verify(redisRepository).cacheMode(10L, false);
        verify(assignmentService, never()).getEligibleSalesUsers();
    }

    @Test
    void managerPauseTreatsMissingPreferenceAsAlreadyPaused() {
        when(preferenceMapper.selectByUserId(10L)).thenReturn(null);

        assertFalse(service.pausePreferenceByManager(10L));

        verify(preferenceMapper, never()).insert(org.mockito.ArgumentMatchers.<SalesDispatchPreferenceDO>any());
        verify(redisRepository).cacheMode(10L, false);
    }

    @Test
    void managerPauseRestoresRedisModeWhenOuterTransactionRollsBack() {
        SalesDispatchPreferenceDO preference = new SalesDispatchPreferenceDO();
        preference.setUserId(10L);
        preference.setAcceptingEnabled(true);
        when(preferenceMapper.selectByUserId(10L)).thenReturn(preference);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        assertTrue(service.pausePreferenceByManager(10L));
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(redisRepository).cacheMode(10L, false);
        verify(redisRepository).cacheMode(10L, true);
    }

    private static LeadAssignmentUserRespVO salesUser(Long id) {
        LeadAssignmentUserRespVO user = new LeadAssignmentUserRespVO();
        user.setId(id);
        return user;
    }
}
