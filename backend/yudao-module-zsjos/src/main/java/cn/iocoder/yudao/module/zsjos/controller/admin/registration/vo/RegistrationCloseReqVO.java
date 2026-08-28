package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationCloseReqVO {
    @NotNull private Integer version;
    @NotBlankIdempotency private String idempotencyKey;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
