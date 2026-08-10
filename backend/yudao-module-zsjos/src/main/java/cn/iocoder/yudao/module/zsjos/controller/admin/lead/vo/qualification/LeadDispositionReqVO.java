package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadDispositionReqVO extends LeadQualificationCommandReqVO {
    @NotBlank
    @Size(max = 500)
    private String reason;
}
