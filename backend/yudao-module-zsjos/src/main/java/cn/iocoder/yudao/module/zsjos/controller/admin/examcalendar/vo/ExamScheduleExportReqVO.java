package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExamScheduleExportReqVO extends PageParam {
    @NotNull private LocalDate rangeStart;
    @NotNull private LocalDate rangeEnd;
    @Pattern(regexp = "DRAFT|PUBLISHED|REVOKED") private String recordStatus;

    @AssertTrue(message = "结束日期不能早于开始日期")
    public boolean isDateRangeValid() {
        return rangeStart == null || rangeEnd == null || !rangeEnd.isBefore(rangeStart);
    }
}
