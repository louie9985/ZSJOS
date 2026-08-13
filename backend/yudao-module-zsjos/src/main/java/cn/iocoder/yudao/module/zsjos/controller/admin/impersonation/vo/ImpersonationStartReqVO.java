package cn.iocoder.yudao.module.zsjos.controller.admin.impersonation.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ImpersonationStartReqVO {
    @NotNull private Long targetUserId;
    @NotBlank @Size(max = 500) private String reason;
}
