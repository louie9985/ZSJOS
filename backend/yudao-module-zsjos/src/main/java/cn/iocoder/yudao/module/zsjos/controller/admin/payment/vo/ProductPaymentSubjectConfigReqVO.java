package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 产品支付主体配置 Request VO")
@Data
public class ProductPaymentSubjectConfigReqVO {

    @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "产品编号不能为空")
    private Long productId;

    @Schema(description = "支付主体编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "支付主体编号不能为空")
    private Long paymentSubjectId;
}
