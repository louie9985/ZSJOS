package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeadQualificationCommandReqVO {
    @NotBlank
    @Size(max = 40)
    private String idempotencyKey;

}
