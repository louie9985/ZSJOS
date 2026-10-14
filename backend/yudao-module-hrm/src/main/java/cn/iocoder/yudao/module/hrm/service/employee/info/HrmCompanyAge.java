package cn.iocoder.yudao.module.hrm.service.employee.info;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 司龄按实际天数折算；年龄仍使用框架的完整周年算法。 */
public final class HrmCompanyAge {
    private HrmCompanyAge() {}

    public static BigDecimal calculate(LocalDate start, LocalDate end) {
        long days = start == null || end == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(start, end));
        return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);
    }
}
