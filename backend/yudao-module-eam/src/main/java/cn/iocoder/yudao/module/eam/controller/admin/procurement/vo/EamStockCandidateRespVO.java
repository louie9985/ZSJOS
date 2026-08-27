package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import lombok.Data;

@Data
public class EamStockCandidateRespVO {
    private String candidateType;
    private Long assetId;
    private String assetCode;
    private Long stockBalanceId;
    private String name;
    private Long categoryId;
    private Integer availableQuantity;
    private String unit;
}
