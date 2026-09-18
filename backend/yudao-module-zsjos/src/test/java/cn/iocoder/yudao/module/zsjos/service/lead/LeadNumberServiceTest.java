package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadNoDailyCounterMapper;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadNumberServiceTest {

    @InjectMocks private LeadNumberService service;
    @Mock private LeadNoDailyCounterMapper counterMapper;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 14);

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
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(1L);

        assertEquals("KZ202608141530450001", service.next(submittedAt));

        var order = inOrder(counterMapper);
        order.verify(counterMapper).insertIfAbsent(7L, DATE);
        order.verify(counterMapper).increment(7L, DATE, 9999L);
        order.verify(counterMapper).selectReservedValue(7L, DATE);
    }

    @Test
    void wrapsSequenceAfter9999() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(1L);

        assertEquals("KZ202608142359590001", service.next(submittedAt));
    }

    @Test
    void supportsTheLastSequenceValue() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(9999L);

        assertEquals("KZ202608142359599999", service.next(submittedAt));
    }

    @Test
    void incrementsWithinSameTenantAndBusinessDate() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 14, 9, 0, 0);
        LocalDateTime second = LocalDateTime.of(2026, 8, 14, 9, 0, 1);
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(1L, 2L);

        assertEquals("KZ202608140900000001", service.next(first));
        assertEquals("KZ202608140900010002", service.next(second));

        verify(counterMapper, org.mockito.Mockito.times(2)).increment(7L, DATE, 9999L);
    }

    @Test
    void resetsSequenceAcrossBeijingBusinessDates() {
        LocalDate next = LocalDate.of(2026, 8, 15);
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(9L);
        when(counterMapper.selectReservedValue(7L, next)).thenReturn(1L);

        assertEquals("KZ202608142359590009",
                service.next(LocalDateTime.of(2026, 8, 14, 23, 59, 59)));
        assertEquals("KZ202608150000000001",
                service.next(LocalDateTime.of(2026, 8, 15, 0, 0, 0)));

        verify(counterMapper).insertIfAbsent(7L, DATE);
        verify(counterMapper).insertIfAbsent(7L, next);
    }

    @Test
    void reservesSequencesIndependentlyPerTenant() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 15, 30, 45);
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(1L);
        when(counterMapper.selectReservedValue(8L, DATE)).thenReturn(1L);

        assertEquals("KZ202608141530450001", service.next(submittedAt));
        TenantContextHolder.setTenantId(8L);
        assertEquals("KZ202608141530450001", service.next(submittedAt));

        verify(counterMapper).insertIfAbsent(7L, DATE);
        verify(counterMapper).insertIfAbsent(8L, DATE);
    }

    @Test
    void rejectsMissingReservedSequence() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 15, 30, 45);
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> service.next(submittedAt));
    }

    /**
     * 回归守卫：计数器表的 AUTO_INCREMENT 受历史导入影响可能远大于每日上限。
     * 若取号又从自增主键派生，就会拿回 5 位数并把当天首笔编号打崩。
     */
    @Test
    void rejectsAnAllocatedPrimaryKeyLeakingIntoTheSequence() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 14, 15, 30, 45);
        when(counterMapper.selectReservedValue(7L, DATE)).thenReturn(12599L);

        assertThrows(IllegalStateException.class, () -> service.next(submittedAt));
    }

    /**
     * 回归守卫：{@code LAST_INSERT_ID(expr)} 会被本次 INSERT 生成的自增主键覆盖，
     * 导致当天首笔取号读到计数器表主键。预占语句里不得再出现 LAST_INSERT_ID。
     */
    @Test
    void reservingNeverDerivesSequenceFromLastInsertId() throws Exception {
        String insertSql = String.join(" ", LeadNoDailyCounterMapper.class
                .getMethod("insertIfAbsent", Long.class, LocalDate.class)
                .getAnnotation(Insert.class).value());
        assertTrue(insertSql.contains("INSERT IGNORE"), insertSql);
        assertFalse(insertSql.contains("LAST_INSERT_ID"), insertSql);

        String updateSql = String.join(" ", LeadNoDailyCounterMapper.class
                .getMethod("increment", Long.class, LocalDate.class, long.class)
                .getAnnotation(org.apache.ibatis.annotations.Update.class).value());
        assertFalse(updateSql.contains("LAST_INSERT_ID"), updateSql);
        assertTrue(updateSql.contains("IF(current_value >= #{maxSequence}, 1, current_value + 1)"));
    }

    /** 读回必须带 FOR UPDATE：普通快照读会拿到旧值，静默发出重复序号。 */
    @Test
    void readbackTakesTheRowLock() throws Exception {
        String sql = String.join(" ", LeadNoDailyCounterMapper.class
                .getMethod("selectReservedValue", Long.class, LocalDate.class)
                .getAnnotation(Select.class).value());

        assertTrue(sql.contains("FOR UPDATE"), sql);
    }
}
