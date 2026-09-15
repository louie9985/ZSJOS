package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 支付主体配置 Response VO")
@Data
public class PaymentSubjectRespVO {

    @Schema(description = "主体编号", example = "1")
    private Long id;

    @Schema(description = "主体编码", example = "zsj_health")
    private String subjectCode;

    @Schema(description = "主体名称", example = "中世健健康管理有限公司")
    private String subjectName;

    @Schema(description = "通联商户号")
    private String cusid;

    @Schema(description = "通联应用ID")
    private String appid;

    @Schema(description = "通联机构号")
    private String orgid;

    @Schema(description = "商户RSA私钥（脱敏）")
    private String merchantPrivateKey;

    @Schema(description = "通联平台RSA公钥（脱敏）")
    private String platformPublicKey;

    @Schema(description = "RSA签名类型", example = "RSA2")
    private String rsaType;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

    @Schema(description = "是否默认主体", example = "false")
    private Boolean isDefault;

    @Schema(description = "显示排序", example = "0")
    private Integer sort;

    @Schema(description = "备注", example = "用于学校收款")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
