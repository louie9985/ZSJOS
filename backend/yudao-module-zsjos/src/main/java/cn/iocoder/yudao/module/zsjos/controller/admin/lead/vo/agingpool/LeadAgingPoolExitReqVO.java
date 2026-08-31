package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeadAgingPoolExitReqVO {
    @NotBlank @Size(max = 500) private String reason;
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
