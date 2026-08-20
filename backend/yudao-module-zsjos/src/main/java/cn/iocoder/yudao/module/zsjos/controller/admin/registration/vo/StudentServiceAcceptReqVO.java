package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudentServiceAcceptReqVO {
    @NotNull private Integer version;
    @NotBlank private String idempotencyKey;
}
