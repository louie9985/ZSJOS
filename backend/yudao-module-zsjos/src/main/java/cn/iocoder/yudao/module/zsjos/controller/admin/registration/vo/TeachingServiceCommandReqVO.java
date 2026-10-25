package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TeachingServiceCommandReqVO {
    @NotBlank @Size(max=500) private String reason;
    @NotNull private Integer version;
    @NotBlank @Size(max=100) private String idempotencyKey;
    private Long targetClassId;
}
