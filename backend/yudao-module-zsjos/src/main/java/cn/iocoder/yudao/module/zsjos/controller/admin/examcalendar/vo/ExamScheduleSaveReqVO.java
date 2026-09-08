package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamScheduleSaveReqVO {
    @NotBlank @Size(max = 16) private String scheduleType;
    private LocalDate exactDate;
    private LocalDate roughStartDate;
    private LocalDate roughEndDate;
    private Long categoryId;
    private Long productId;
    @Size(max = 100) private java.util.Map<@NotBlank String, @NotBlank String> selectedAttrs;
    @Size(max = 100) private java.util.Set<@NotBlank String> clearedInvalidAttrs;
    @Size(max = 1000) private String remark;
}
