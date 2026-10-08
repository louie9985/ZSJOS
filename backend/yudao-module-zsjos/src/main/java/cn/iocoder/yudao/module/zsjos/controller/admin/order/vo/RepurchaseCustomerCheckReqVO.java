package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RepurchaseCustomerCheckReqVO {
    @NotBlank @Size(max = 100) private String customerName;
    @Size(max = 32) private String customerMobile;
    @Size(max = 64) private String customerWechatId;
}
