package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubordinateLeadActionReqVO {
    @NotBlank
    @Size(max = 500)
    private String reason;
    @NotBlank
    @Size(max = 40)
    private String idempotencyKey;
    private Long targetUserId;
    private Long collaboratorUserId;
}
