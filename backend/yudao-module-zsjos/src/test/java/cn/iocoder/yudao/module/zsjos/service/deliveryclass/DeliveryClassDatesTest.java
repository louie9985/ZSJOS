package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassSaveReqVO;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryClassDatesTest {
    @Test void readsDatesFromNamedAndDateOnlyHistoricalSnapshots() {
        var start = LocalDate.of(2026, 10, 30);
        var end = LocalDate.of(2026, 11, 2);
        assertEquals(new DeliveryClassDates(start, end), DeliveryClassDates.fromSnapshot("考期 · 2026-10-30~2026-11-02"));
        assertEquals(new DeliveryClassDates(start, start), DeliveryClassDates.fromSnapshot("2026-10-30"));
        assertEquals("2026-10-30~2026-11-02", new DeliveryClassDates(start, end).snapshot());
        assertEquals("2026-10-30", new DeliveryClassDates(start, start).snapshot());
        assertNull(DeliveryClassDates.fromSnapshot("旧考期无日期"));
        assertNull(DeliveryClassDates.fromSnapshot("2026-02-30"));
        assertNull(DeliveryClassDates.fromSnapshot("2026-11-02~2026-10-30"));
    }

    @Test void acceptsMissingDatesManualDatesAndLegacyIdsButRejectsInvalidRanges() {
        var req = new DeliveryClassSaveReqVO();
        assertTrue(req.isExamDateValid());
        req.setExamScheduleId(1L);
        assertTrue(req.isExamDateValid());
        req.setExamScheduleId(null);
        req.setStartDate(LocalDate.of(2020, 1, 1));
        assertTrue(req.isExamDateValid());
        req.setEndDate(LocalDate.of(2019, 12, 31));
        assertFalse(req.isExamDateValid());
        req.setEndDate(LocalDate.of(2020, 1, 2));
        assertTrue(req.isExamDateValid());
        req.setStartDate(null);
        req.setExamScheduleId(1L);
        assertFalse(req.isExamDateValid());
    }
}
