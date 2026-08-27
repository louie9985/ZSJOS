package cn.iocoder.yudao.module.eam.dal.dataobject.stock;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.Map;

@TableName(value = "eam_stock_balance", autoResultMap = true)
@KeySequence("eam_stock_balance_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamStockBalanceDO extends BaseDO {
    @TableId
    private Long id;
    private String name;
    private Long categoryId;
    private Integer managementMode;
    private Integer deliveryMode;
    private Integer custodyMode;
    private String unit;
    private String attributeSignature;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extFields;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldLabels;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldDictTypes;
    private Integer onHandQuantity;
    private Integer reservedQuantity;
    private Integer frozenQuantity;
    private Integer minimumQuantity;
    private LocalDate nextExpiryDate;
    private Integer version;

    public int getAvailableQuantity() {
        return value(onHandQuantity) - value(reservedQuantity) - value(frozenQuantity);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
