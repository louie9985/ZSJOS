package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 支付主体配置创建/修改 Request VO")
@Data
public class PaymentSubjectSaveReqVO {

    @Schema(description = "主体编号", example = "1")
    private Long id;

    @Schema(description = "主体编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "zsj_health")
    @NotBlank(message = "主体编码不能为空")
    private String subjectCode;

    @Schema(description = "主体名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "中世健健康管理有限公司")
    @NotBlank(message = "主体名称不能为空")
    private String subjectName;

    @Schema(description = "通联商户号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商户号不能为空")
    private String cusid;

    @Schema(description = "通联应用ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "应用ID不能为空")
    private String appid;

    @Schema(description = "通联机构号")
    private String orgid;

    @Schema(description = "商户RSA私钥", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商户私钥不能为空")
    private String merchantPrivateKey;

    @Schema(description = "通联平台RSA公钥", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "平台公钥不能为空")
    private String platformPublicKey;

    @Schema(description = "RSA签名类型", example = "RSA2")
    private String rsaType;

    @Schema(description = "状态（0启用 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "是否默认主体", example = "false")
    private Boolean isDefault;

    @Schema(description = "显示排序", example = "0")
    private Integer sort;

    @Schema(description = "备注", example = "用于学校收款")
    private String remark;
}
