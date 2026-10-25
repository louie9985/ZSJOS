package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class MediaAccountStageTimingTest {
    @Test void stageTransitionsPreserveUnknownAndRestartOnReturn() {
        var now = LocalDateTime.of(2026, 10, 21, 15, 0);
        var account = new MediaAccountDO();
        assertEquals(now, MediaAccountStageTiming.enteredAt(account, "S1", now));
        account.setSStage("S1");
        assertNull(MediaAccountStageTiming.enteredAt(account, "S1", now));
        account.setSStageEnteredAt(now.minusDays(9));
        assertEquals(now.minusDays(9), MediaAccountStageTiming.enteredAt(account, "S1", now));
        assertEquals(now, MediaAccountStageTiming.enteredAt(account, "S2", now));
        account.setSStage("S2");
        assertEquals(now, MediaAccountStageTiming.enteredAt(account, "S1", now));
    }
    @Test void naturalDaysUseShanghaiAcrossMidnightMonthAndYear() {
        for (var start : new LocalDateTime[]{LocalDateTime.of(2026, 10, 21, 23, 59),
                LocalDateTime.of(2026, 10, 31, 23, 59), LocalDateTime.of(2026, 12, 31, 23, 59)}) {
            var account = new MediaAccountDO().setSStage("S1").setSStageEnteredAt(start);
            assertEquals(0L, MediaAccountStageTiming.snapshot(account, start).get("sStageDurationDays"));
            assertEquals(1L, MediaAccountStageTiming.snapshot(account, start.plusMinutes(2)).get("sStageDurationDays"));
            assertEquals(start.atZone(MediaAccountStageTiming.ZONE).toInstant().toEpochMilli(),
                    MediaAccountStageTiming.snapshot(account, start).get("sStageEnteredAt"));
            assertFalse(MediaAccountStageTiming.snapshot(account, start.minusSeconds(1)).containsKey("sStageDurationDays"));
        }
    }
    @Test void missingAndRecoveredOriginsAreExplicit() {
        var now = LocalDateTime.of(2026, 10, 21, 0, 0);
        var account = new MediaAccountDO().setSStage("S1");
        assertFalse(MediaAccountStageTiming.snapshot(account, now).containsKey("sStageDurationDays"));
        account.setSStageEnteredAt(now);
        account.setDetailValuesJson("{\"_stageTimingRecovery\":{\"stage\":\"S1\",\"enteredAt\":" + MediaAccountStageTiming.millis(now) + "}}");
        assertEquals("HISTORY_RECONSTRUCTED", MediaAccountStageTiming.snapshot(account, now).get("sStageTimingSource"));
        account.setSStage("S2");
        assertEquals("RECORDED", MediaAccountStageTiming.snapshot(account, now).get("sStageTimingSource"));
    }
}
