package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaAccountDiagnosisSchedulerTest {
    @Test void effectiveDayUsesCreationDayAsDayOne() {
        MediaAccountDO account = new MediaAccountDO(); account.setCreateTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        assertEquals(1, MediaAccountDiagnosisScheduler.effectiveDay(account, LocalDate.of(2026, 9, 1)));
        assertEquals(7, MediaAccountDiagnosisScheduler.effectiveDay(account, LocalDate.of(2026, 9, 7)));
        assertEquals(28, MediaAccountDiagnosisScheduler.effectiveDay(account, LocalDate.of(2026, 9, 28)));
    }

    @Test void effectiveDaySubtractsMaintenancePause() {
        MediaAccountDO account = new MediaAccountDO(); account.setCreateTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        account.setMaintenanceStartDate(LocalDate.of(2026, 9, 5)); account.setMaintenanceEndDate(LocalDate.of(2026, 9, 7));
        assertEquals(4, MediaAccountDiagnosisScheduler.effectiveDay(account, LocalDate.of(2026, 9, 7)));
        assertEquals(5, MediaAccountDiagnosisScheduler.effectiveDay(account, LocalDate.of(2026, 9, 8)));
    }
}
