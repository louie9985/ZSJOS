package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderRepurchaseReqVO {
    private Long leadContextId;
    @Size(max = 100) private String customerName;
    @Size(max = 32) private String customerMobile;
    @Size(max = 128) private String customerWechatId;
    @NotBlank @Size(max = 1000) private String repurchaseReason;
    @NotNull @Valid private SalesOrderSubmitReqVO order;
}
