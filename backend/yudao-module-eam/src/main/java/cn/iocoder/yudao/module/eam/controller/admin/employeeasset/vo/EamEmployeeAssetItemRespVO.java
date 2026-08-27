package cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EamEmployeeAssetItemRespVO {
    /** SERIALIZED_ASSET 或 BATCH_HOLDING。 */
    private String itemType;
    private Long holdingId;
    private Long assetId;
    private String assetCode;
    private Long stockBalanceId;
    private String name;
    private Integer quantity;
    private String unit;
    private Integer custodyMode;
    private Integer status;
    private LocalDateTime signedAt;
    private LocalDateTime returnAppliedAt;
    private Integer returnResult;
}
