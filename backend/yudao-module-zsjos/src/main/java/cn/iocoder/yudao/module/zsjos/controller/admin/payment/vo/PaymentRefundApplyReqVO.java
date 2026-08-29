package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRefundApplyReqVO {
    @NotNull private Long paymentTransactionId;
    private Long orderId;
    @NotBlank private String reason;
    @NotBlank private String idempotencyKey;
}
