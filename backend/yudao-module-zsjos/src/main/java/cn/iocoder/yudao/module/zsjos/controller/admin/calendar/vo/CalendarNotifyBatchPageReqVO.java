package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CalendarNotifyBatchPageReqVO extends PageParam {
    @NotBlank @Pattern(regexp = "EXAM|COURSE") private String calendarType;
    @Positive private Long calendarId;
    @Pattern(regexp = "SUBMITTED|PARTIAL|SUCCEEDED|FAILED|SKIPPED") private String status;
}
