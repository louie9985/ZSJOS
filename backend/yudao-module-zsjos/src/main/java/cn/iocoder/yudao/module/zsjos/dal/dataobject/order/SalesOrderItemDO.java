package cn.iocoder.yudao.module.zsjos.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("zsjos_order_item")
@KeySequence("zsjos_order_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private Long productId;
    private Long skuId;
    private String productRef;
    private String skuRef;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private String productSnapshot;
    private String productRuleSnapshot;
    private String productRuleVersion;
}
