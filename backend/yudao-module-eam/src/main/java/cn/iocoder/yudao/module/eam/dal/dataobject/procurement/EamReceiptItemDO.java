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
import java.util.List;
import java.util.Map;

@TableName(value = "eam_receipt_item", autoResultMap = true)
@KeySequence("eam_receipt_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamReceiptItemDO extends BaseDO {
    @TableId
    private Long id;
    private Long receiptId;
    private Long purchaseItemId;
    private Long stockBalanceId;
    private Integer quantity;
    private BigDecimal unitPrice;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> serialNumbers;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> actualExtFields;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> actualExtFieldLabels;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> actualExtFieldDictTypes;
}
