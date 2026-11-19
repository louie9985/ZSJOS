package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationTransferReqVO extends RegistrationVersionReqVO {
    @NotNull private Long targetUserId;
    @NotBlank @Size(max = 180) private String reason;
}
