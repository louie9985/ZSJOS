package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeadAgingPoolAssignReqVO {
    @NotNull private Long salesUserId;
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
