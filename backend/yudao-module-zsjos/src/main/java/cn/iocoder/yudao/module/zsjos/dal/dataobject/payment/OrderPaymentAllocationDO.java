package cn.iocoder.yudao.module.zsjos.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("zsjos_order_payment_allocation")
@KeySequence("zsjos_order_payment_allocation_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class OrderPaymentAllocationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private Long paymentTransactionId;
    private Long customerAccountLedgerId;
    private BigDecimal allocatedAmount;
    private String currency;
    private LocalDateTime allocatedAt;
    private String idempotencyKey;
}
