package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonNoDailyCounterMapper;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonNumberServiceTest {
    @InjectMocks private PersonNumberService service;
    @Mock private PersonNoDailyCounterMapper counterMapper;

    @BeforeEach
    void setUp() { TenantContextHolder.setTenantId(1L); }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void formatsBeijingTimestampAndDailySequence() {
        when(counterMapper.selectReservedValue()).thenReturn(1L);

        assertEquals("XY202608241430250001",
                service.next(LocalDateTime.of(2026, 8, 24, 14, 30, 25)));
        org.mockito.Mockito.verify(counterMapper).reserve(eq(1L), eq(java.time.LocalDate.of(2026, 8, 24)));
    }

    @Test
    void supportsTheLastSequenceValue() {
        when(counterMapper.selectReservedValue()).thenReturn(9999L);

        assertEquals("XY202608241430259999",
                service.next(LocalDateTime.of(2026, 8, 24, 14, 30, 25)));
    }

    @Test
    void rejectsAnInvalidDatabaseSequence() {
        when(counterMapper.selectReservedValue()).thenReturn(0L);

        assertThrows(IllegalStateException.class,
                () -> service.next(LocalDateTime.of(2026, 8, 24, 14, 30, 25)));
    }

    @Test
    void counterReservationUsesConnectionScopedAtomicWrap() throws Exception {
        Insert insert = PersonNoDailyCounterMapper.class
                .getMethod("reserve", Long.class, java.time.LocalDate.class).getAnnotation(Insert.class);
        String sql = String.join(" ", insert.value());

        assertTrue(sql.contains("LAST_INSERT_ID(1)"));
        assertTrue(sql.contains("LAST_INSERT_ID(IF(current_value >= 9999, 1, current_value + 1))"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
    }
}
