package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class CalendarSearchReqVO extends PageParam {
    @NotBlank @Size(max = 100) private String keyword;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate rangeStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate rangeEnd;
    @NotNull @Pattern(regexp = "nearest|asc|desc") private String sort = "nearest";
    public CalendarSearchReqVO() { setPageSize(20); }
    public void setKeyword(String value) { keyword = value == null ? null : value.trim(); }
    @Override @Max(100) public Integer getPageSize() { return super.getPageSize(); }
    @AssertTrue(message = "结束日期不能早于开始日期")
    public boolean isDateRangeValid() { return rangeStart == null || rangeEnd == null || !rangeEnd.isBefore(rangeStart); }
}

