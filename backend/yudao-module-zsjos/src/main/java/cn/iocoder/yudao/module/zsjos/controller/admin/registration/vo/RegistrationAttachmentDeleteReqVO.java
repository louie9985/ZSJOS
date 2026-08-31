package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationAttachmentDeleteReqVO {
    @NotNull private Integer version;
    @NotBlankIdempotency private String idempotencyKey;
}
