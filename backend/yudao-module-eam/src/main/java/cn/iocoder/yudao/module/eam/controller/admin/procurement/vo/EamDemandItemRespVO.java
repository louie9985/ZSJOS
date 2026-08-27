package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import lombok.Data;
import java.util.Map;

@Data
public class EamDemandItemRespVO {
    private Long id;
    private Long demandId;
    private String name;
    private Long categoryId;
    private Integer managementMode;
    private Integer deliveryMode;
    private String deliveryModeLabelSnapshot;
    private Integer custodyMode;
    private String custodyModeLabelSnapshot;
    private Integer quantity;
    private String unit;
    private Map<String, Object> extFields;
    private Map<String, String> extFieldLabels;
    private Map<String, String> extFieldDictTypes;
    private Integer reservedQuantity;
    private Integer purchasedQuantity;
    private Integer fulfilledQuantity;
    private Integer closedQuantity;
}
