package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.NewcomerPoolConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.NewcomerPoolDailyDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.NewcomerPoolConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.NewcomerPoolDailyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewcomerPoolServiceTest {
    @InjectMocks private NewcomerPoolService service;
    @Mock private NewcomerPoolConfigMapper configMapper;
    @Mock private NewcomerPoolDailyMapper dailyMapper;

    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void quotaExhaustionLeavesOnlyUsersWithRemainingQuota() {
        when(configMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(config(10L, true, 2), config(11L, true, 3)));
        when(dailyMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(daily(10L, 2), daily(11L, 2)));
        assertEquals(Set.of(11L), service.availableUsers(Set.of(10L, 11L), LocalDate.of(2026, 10, 9)));
    }

    @Test void currentReadHonorsLoweredQuotaAndDisabledConfig() {
        when(configMapper.selectByUserIdForUpdate(10L)).thenReturn(config(10L, true, 1));
        when(dailyMapper.selectByUserAndDateForUpdate(eq(10L), any())).thenReturn(daily(10L, 2));
        assertFalse(service.lockAvailable(10L));
        when(configMapper.selectByUserIdForUpdate(10L)).thenReturn(config(10L, false, 10));
        assertFalse(service.lockAvailable(10L));
    }

    @Test void reenabledConfigRetainsUsageAndNextDayHasFreshQuota() {
        when(configMapper.selectByUserIdForUpdate(10L)).thenReturn(config(10L, true, 2));
        when(dailyMapper.selectByUserAndDateForUpdate(eq(10L), any())).thenReturn(daily(10L, 2), null);
        assertFalse(service.lockAvailable(10L));
        assertTrue(service.lockAvailable(10L));
    }

    @Test void acceptanceUsesBeijingNaturalDayAndExplicitTenant() {
        TenantContextHolder.setTenantId(7L);
        var zone = ZoneId.of("Asia/Shanghai");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-09T15:59:59Z"), zone));
        service.recordAcceptance(10L);
        verify(dailyMapper).increment(7L, 10L, LocalDate.of(2026, 10, 9));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-09T16:00:00Z"), zone));
        service.recordAcceptance(10L);
        verify(dailyMapper).increment(7L, 10L, LocalDate.of(2026, 10, 10));
    }

    @Test void configChangesNeverResetDailyCounts() {
        TenantContextHolder.setTenantId(7L);
        service.update(10L, false, 5);
        service.update(10L, true, 3);
        verify(configMapper).save(eq(7L), eq(10L), eq(false), eq(5), anyString());
        verify(configMapper).save(eq(7L), eq(10L), eq(true), eq(3), anyString());
        verifyNoInteractions(dailyMapper);
    }

    private NewcomerPoolConfigDO config(long userId, boolean enabled, int quota) {
        var value = new NewcomerPoolConfigDO();
        value.setUserId(userId); value.setEnabled(enabled); value.setDailyQuota(quota); return value;
    }

    private NewcomerPoolDailyDO daily(long userId, int count) {
        var value = new NewcomerPoolDailyDO();
        value.setUserId(userId); value.setAcceptedCount(count); return value;
    }
}
