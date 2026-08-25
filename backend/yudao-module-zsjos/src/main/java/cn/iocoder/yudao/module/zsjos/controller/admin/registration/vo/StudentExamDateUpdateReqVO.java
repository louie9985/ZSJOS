package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentExamDateUpdateReqVO {
    @NotNull private LocalDate examDate;
    @NotNull private Integer version;
    @NotBlank private String idempotencyKey;
}
