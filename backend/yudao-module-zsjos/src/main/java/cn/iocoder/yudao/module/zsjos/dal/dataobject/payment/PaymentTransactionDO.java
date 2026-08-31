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

@TableName("zsjos_payment_transaction")
@KeySequence("zsjos_payment_transaction_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PaymentTransactionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long paymentOrderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private LocalDateTime paidAt;
    private String externalChannel;
    private String externalTransactionNo;
    private String payerReference;
    private String callbackEventId;
    private String evidenceRefs;
    private String reqsn;
    private String trxId;
    private String channelTransactionNo;
    private Integer amountFen;
    private String source;
}
