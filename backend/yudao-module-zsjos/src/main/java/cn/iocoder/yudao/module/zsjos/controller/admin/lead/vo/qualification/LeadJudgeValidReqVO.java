package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadJudgeValidReqVO extends LeadQualificationCommandReqVO {
    @Size(max = 100) private String leadCategory;
    @NotBlank @Size(max = 2000) private String remark;
}
