package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class EamStockBalanceRespVO {
    private Long id;
    private String name;
    private Long categoryId;
    private Integer managementMode;
    private Integer deliveryMode;
    private Integer custodyMode;
    private String unit;
    private Map<String, Object> extFields;
    private Map<String, String> extFieldLabels;
    private Map<String, String> extFieldDictTypes;
    private Integer onHandQuantity;
    private Integer reservedQuantity;
    private Integer frozenQuantity;
    private Integer availableQuantity;
    private Integer minimumQuantity;
    private LocalDate nextExpiryDate;
}
