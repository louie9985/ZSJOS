package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MediaAccountCalendarScheduleReqVO {
    @NotNull private LocalDate rangeStart;
    @NotNull private LocalDate rangeEnd;
    private String keyword;
    private String currentStatusValue;
    private String stageValue;
    private Long directorUserId;
    private Long operatorUserId;
}
