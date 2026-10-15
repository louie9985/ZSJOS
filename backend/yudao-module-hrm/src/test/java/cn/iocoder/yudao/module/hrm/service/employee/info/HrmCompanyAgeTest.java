package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.enums.employee.info.HrmEmployeeStatusEnum;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class HrmCompanyAgeTest {
    @Test void actualDaysRoundHalfUpAndKeepTwoDecimals() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        assertEquals(new BigDecimal("0.00"), HrmCompanyAge.calculate(start, start));
        assertEquals(new BigDecimal("0.00"), HrmCompanyAge.calculate(start, start.minusDays(1)));
        assertEquals(new BigDecimal("0.00"), HrmCompanyAge.calculate(null, start));
        assertEquals(new BigDecimal("0.27"), HrmCompanyAge.calculate(start, start.plusDays(100)));
        assertEquals(new BigDecimal("0.50"), HrmCompanyAge.calculate(start, start.plusDays(182)));
        assertEquals(new BigDecimal("0.01"), HrmCompanyAge.calculate(start, start.plusDays(2)));
        assertEquals(new BigDecimal("1.00"), HrmCompanyAge.calculate(start, start.plusYears(1)));
        assertEquals(new BigDecimal("2.74"), HrmCompanyAge.calculate(start, start.plusDays(1000)));
    }

    @Test void trialBelongsToInformalEmploymentAndDatesRemainIndependent() {
        var entry = LocalDate.now().minusDays(10).atStartOfDay();
        var request = new HrmEmployeeSaveReqVO().setType(2).setStatus(9).setEntryTime(entry)
                .setTrialDays(3).setPlannedTrialEndTime(entry.plusDays(3)).setActualTrialEndTime(entry.plusDays(4));
        assertTrue(request.isStatusValid()); assertTrue(request.isTrialDatesValid()); assertTrue(request.isActualDatesValid());
        assertTrue(HrmEmployeeStatusEnum.CONVERTIBLE_TO_FULL_TIME_STATUSES.contains(9));
        request.setType(1); assertFalse(request.isStatusValid());
        request.setActualTrialEndTime(entry.minusDays(1)); assertFalse(request.isTrialDatesValid());
        request.setActualTrialEndTime(LocalDate.now().plusDays(1).atStartOfDay()); assertFalse(request.isActualDatesValid());
        request.setActualTrialEndTime(null); assertTrue(request.isActualDatesValid());
    }
}
