package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class EamPurchaseItemRespVO {
    private Long id;
    private Long purchaseId;
    private String name;
    private Long categoryId;
    private Integer managementMode;
    private Integer deliveryMode;
    private String deliveryModeLabelSnapshot;
    private Integer custodyMode;
    private String custodyModeLabelSnapshot;
    private Integer quantity;
    private Integer receivedQuantity;
    private Integer returnedQuantity;
    private Integer shortClosedQuantity;
    private String shortCloseRemark;
    private String unit;
    private BigDecimal unitPrice;
    private Map<String, Object> extFields;
    private Map<String, String> extFieldLabels;
    private Map<String, String> extFieldDictTypes;
}
