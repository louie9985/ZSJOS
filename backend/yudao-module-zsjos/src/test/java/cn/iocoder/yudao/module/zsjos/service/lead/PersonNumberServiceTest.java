package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonNoDailyCounterMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonNumberServiceTest {
    @InjectMocks private PersonNumberService service;
    @Mock private PersonNoDailyCounterMapper counterMapper;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 24);

    @BeforeEach
    void setUp() { TenantContextHolder.setTenantId(1L); }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void formatsBeijingTimestampAndDailySequence() {
        when(counterMapper.selectReservedValue(1L, DATE)).thenReturn(1L);

        assertEquals("XY202608241430250001",
                service.next(LocalDateTime.of(2026, 8, 24, 14, 30, 25)));
        org.mockito.Mockito.verify(counterMapper).reserve(eq(1L), eq(DATE), eq(9999L));
    }

    @Test
    void supportsTheLastSequenceValue() {
        when(counterMapper.selectReservedValue(1L, DATE)).thenReturn(9999L);

        assertEquals("XY202608241430259999",
                service.next(LocalDateTime.of(2026, 8, 24, 14, 30, 25)));
    }

    @Test
    void rejectsAnInvalidDatabaseSequence() {
        when(counterMapper.selectReservedValue(1L, DATE)).thenReturn(0L);

        assertThrows(IllegalStateException.class,
                () -> service.next(LocalDateTime.of(2026, 8, 24, 14, 30, 25)));
    }

    /**
     * 回归守卫：计数器表的 AUTO_INCREMENT 受历史导入影响可能远大于每日上限。
     * 若取号又从自增主键派生，就会拿回 5 位数并把当天首笔编号打崩。
     */
    @Test
    void rejectsAnAllocatedPrimaryKeyLeakingIntoTheSequence() {
        when(counterMapper.selectReservedValue(1L, DATE)).thenReturn(12599L);

        assertThrows(IllegalStateException.class,
                () -> service.next(LocalDateTime.of(2026, 8, 24, 14, 30, 25)));
    }

    /**
     * 回归守卫：{@code LAST_INSERT_ID(expr)} 会被本次 INSERT 生成的自增主键覆盖，
     * 导致当天首笔取号读到计数器表主键。预占语句里不得再出现 LAST_INSERT_ID。
     */
    @Test
    void reservingNeverDerivesSequenceFromLastInsertId() throws Exception {
        String insertSql = String.join(" ", PersonNoDailyCounterMapper.class
                .getMethod("reserve", Long.class, LocalDate.class, long.class)
                .getAnnotation(Insert.class).value());

        assertFalse(insertSql.contains("LAST_INSERT_ID"), insertSql);
        assertTrue(insertSql.contains("IF(current_value >= #{maxSequence}, 1, current_value + 1)"));
    }

    /** 读回必须带 FOR UPDATE：普通快照读会拿到旧值，静默发出重复序号。 */
    @Test
    void readbackTakesTheRowLock() throws Exception {
        String sql = String.join(" ", PersonNoDailyCounterMapper.class
                .getMethod("selectReservedValue", Long.class, LocalDate.class)
                .getAnnotation(Select.class).value());

        assertTrue(sql.contains("FOR UPDATE"), sql);
    }
}
