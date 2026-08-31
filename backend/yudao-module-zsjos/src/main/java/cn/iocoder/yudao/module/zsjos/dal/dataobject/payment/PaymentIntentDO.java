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

@TableName("zsjos_payment_order")
@KeySequence("zsjos_payment_order_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PaymentIntentDO extends TenantBaseDO {
    @TableId private Long id;
    private String paymentOrderNo;
    private Long purchaseIntentId;
    private Long leadId;
    private Long personId;
    private Long opportunityId;
    private String status;
    private BigDecimal expectedAmount;
    private String currency;
    private String productItemsSnapshot;
    private String productRuleSnapshot;
    private String productRuleVersion;
    private Long initiatorUserId;
    private String linkTokenHash;
    private String linkUrl;
    private String provider;
    private String channel;
    private String reqsn;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private String closeReason;
    private LocalDateTime queriedAt;
    private Integer version;
}
