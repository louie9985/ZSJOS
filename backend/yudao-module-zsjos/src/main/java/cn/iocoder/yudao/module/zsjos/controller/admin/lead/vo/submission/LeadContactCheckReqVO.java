package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeadContactCheckReqVO {
    @Size(max = 32) private String mobile;
    @Size(max = 64) private String wechatId;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
