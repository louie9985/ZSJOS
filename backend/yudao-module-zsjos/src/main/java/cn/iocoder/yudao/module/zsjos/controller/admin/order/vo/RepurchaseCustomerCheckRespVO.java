package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.Data;

@Data
public class RepurchaseCustomerCheckRespVO {
    private String matchStatus;
    private boolean canRepurchase;
    private String reason;
    private Long personId;
    private String customerName;
    private String maskedMobile;
}
