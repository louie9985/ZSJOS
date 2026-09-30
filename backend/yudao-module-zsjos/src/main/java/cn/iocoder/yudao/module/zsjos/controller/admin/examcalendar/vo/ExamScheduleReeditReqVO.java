package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ExamScheduleReeditReqVO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{16,64}")
    private String operationKey;
}
