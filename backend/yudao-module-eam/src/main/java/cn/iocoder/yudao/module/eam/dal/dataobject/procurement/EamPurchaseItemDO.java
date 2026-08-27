package cn.iocoder.yudao.module.eam.dal.dataobject.procurement;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

@TableName(value = "eam_purchase_item", autoResultMap = true)
@KeySequence("eam_purchase_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamPurchaseItemDO extends BaseDO {
    @TableId
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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extFields;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldLabels;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldDictTypes;
}
