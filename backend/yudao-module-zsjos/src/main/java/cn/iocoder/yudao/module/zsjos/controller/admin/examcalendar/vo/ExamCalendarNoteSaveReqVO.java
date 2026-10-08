package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ExamCalendarNoteSaveReqVO {
    @NotNull @Size(max = 204800) private String content;
    @NotNull @Min(0) private Long version;
}
