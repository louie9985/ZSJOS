package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadNoDailyCounterMapper;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadNumberServiceTest {

    @InjectMocks private LeadNumberService service;
    @Mock private LeadNoDailyCounterMapper counterMapper;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void formatsFirstDailySequenceWithBeijingBusinessTimestamp() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 15, 30, 45);
        when(counterMapper.selectReservedValue()).thenReturn(1L);

        assertEquals("KZ202608141530450001", service.next(submittedAt));

        var order = inOrder(counterMapper);
        order.verify(counterMapper).reserve(7L, LocalDate.of(2026, 8, 14));
        order.verify(counterMapper).selectReservedValue();
    }

    @Test
    void wrapsSequenceAfter9999() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(counterMapper.selectReservedValue()).thenReturn(1L);

        assertEquals("KZ202608142359590001", service.next(submittedAt));
    }

    @Test
    void incrementsWithinSameTenantAndBusinessDate() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 14, 9, 0, 0);
        LocalDateTime second = LocalDateTime.of(2026, 8, 14, 9, 0, 1);
        when(counterMapper.selectReservedValue()).thenReturn(1L, 2L);

        assertEquals("KZ202608140900000001", service.next(first));
        assertEquals("KZ202608140900010002", service.next(second));

        verify(counterMapper, org.mockito.Mockito.times(2)).reserve(7L, LocalDate.of(2026, 8, 14));
    }

    @Test
    void resetsSequenceAcrossBeijingBusinessDates() {
        when(counterMapper.selectReservedValue()).thenReturn(9L, 1L);

        assertEquals("KZ202608142359590009",
                service.next(LocalDateTime.of(2026, 8, 14, 23, 59, 59)));
        assertEquals("KZ202608150000000001",
                service.next(LocalDateTime.of(2026, 8, 15, 0, 0, 0)));

        verify(counterMapper).reserve(7L, LocalDate.of(2026, 8, 14));
        verify(counterMapper).reserve(7L, LocalDate.of(2026, 8, 15));
    }

    @Test
    void reservesSequencesIndependentlyPerTenant() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 15, 30, 45);
        when(counterMapper.selectReservedValue()).thenReturn(1L, 1L);

        assertEquals("KZ202608141530450001", service.next(submittedAt));
        TenantContextHolder.setTenantId(8L);
        assertEquals("KZ202608141530450001", service.next(submittedAt));

        verify(counterMapper).reserve(7L, LocalDate.of(2026, 8, 14));
        verify(counterMapper).reserve(8L, LocalDate.of(2026, 8, 14));
    }

    @Test
    void rejectsMissingReservedSequence() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 15, 30, 45);
        when(counterMapper.selectReservedValue()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> service.next(submittedAt));
    }

    @Test
    void counterReservationUsesConnectionScopedAtomicIncrement() throws Exception {
        Insert insert = LeadNoDailyCounterMapper.class
                .getMethod("reserve", Long.class, LocalDate.class).getAnnotation(Insert.class);
        String sql = String.join(" ", insert.value());

        assertTrue(sql.contains("LAST_INSERT_ID(1)"));
        assertTrue(sql.contains("LAST_INSERT_ID(IF(current_value >= 9999, 1, current_value + 1))"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
    }
}
