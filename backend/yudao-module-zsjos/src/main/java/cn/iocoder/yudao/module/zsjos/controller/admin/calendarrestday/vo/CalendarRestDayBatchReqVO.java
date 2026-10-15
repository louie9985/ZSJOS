package cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CalendarRestDayBatchReqVO {
    @NotEmpty @Size(max = 366)
    private List<@NotNull @Valid CalendarRestDayChangeVO> changes;
}
