package cn.iocoder.yudao.module.eam.dal.dataobject.procurement;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@TableName(value = "eam_demand_item", autoResultMap = true)
@KeySequence("eam_demand_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamDemandItemDO extends BaseDO {
    @TableId
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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extFields;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldLabels;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldDictTypes;
    private Integer reservedQuantity;
    private Integer purchasedQuantity;
    private Integer fulfilledQuantity;
    private Integer closedQuantity;
}
