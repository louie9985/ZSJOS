package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeadUrgeReqVO {
    @NotBlank @Size(max = 500) private String reason;
}
