package cn.iocoder.yudao.module.zsjos.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@TableName("zsjos_purchase_intent")
@KeySequence("zsjos_purchase_intent_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PurchaseIntentDO extends TenantBaseDO {
    @TableId private Long id;
    private String purchaseIntentNo;
    private String collectionMode;
    private String purchaseType;
    private Long leadId;
    private Long personId;
    private Long opportunityId;
    private String sourceKey;
    private Long initiatorUserId;
    private Long ownerUserId;
    private String draftJson;
    private String itemSnapshotJson;
    private BigDecimal totalAmount;
    private String currency;
    private Long currentOrderId;
    private Boolean snapshotLocked;
    private String status;
    private String lastIdempotencyKey;
    private Integer version;
}
