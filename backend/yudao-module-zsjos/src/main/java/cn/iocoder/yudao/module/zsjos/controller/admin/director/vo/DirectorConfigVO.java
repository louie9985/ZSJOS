package cn.iocoder.yudao.module.zsjos.controller.admin.director.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public final class DirectorConfigVO {
    private DirectorConfigVO() {}
    @Data public static class Resp {
        private Long id; private Integer interviewAppointmentHours; private Integer positioningDueHours;
        private Integer trialDays; private Integer version;
    }
    @Data public static class UpdateReq {
        @NotNull @Min(1) @Max(720) private Integer interviewAppointmentHours;
        @NotNull @Min(1) @Max(720) private Integer positioningDueHours;
        @NotNull @Min(1) @Max(365) private Integer trialDays;
        @NotNull private Integer version;
    }
}
