package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data
public class LeadCalendarQueryReqVO {
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;
    @Min(1) @Max(100000) private int pageNo = 1;
    @Min(1) @Max(48) private int pageSize = 24;
    @NotNull @Pattern(regexp = "deadline|category") private String sort = "deadline";
    @NotNull @Pattern(regexp = "asc|desc") private String direction = "asc";

    @AssertTrue(message = "日历查询范围须为 1 至 42 天，结束日期不包含在内")
    public boolean isValidRange() {
        return start == null || end == null || (end.isAfter(start) && ChronoUnit.DAYS.between(start, end) <= 42);
    }
}
