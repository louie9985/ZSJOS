package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 产品支付主体关联 Response VO")
@Data
public class ProductPaymentSubjectRespVO {

    private String productName;
    private java.time.LocalDateTime configTime;

    @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long productId;

    @Schema(description = "支付主体编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long paymentSubjectId;

    @Schema(description = "支付主体编码", example = "ALLINPAY_MAIN")
    private String subjectCode;

    @Schema(description = "支付主体名称", example = "北京主体")
    private String subjectName;
}
