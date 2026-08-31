package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationChecklistItemUpdateReqVO {
    @NotNull private Boolean checked;
    @NotNull private Integer version;
    @NotBlankIdempotency private String idempotencyKey;
}
