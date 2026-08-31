package cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("zsjos_cashback")
@Data
@EqualsAndHashCode(callSuper = true)
public class CashbackDO extends TenantBaseDO {
    @TableId private Long id;
    private String cashbackNo;
    private String businessKey;
    private String type;
    private String status;
    private Long beneficiaryUserId;
    private Long partnerId;
    private Long leadId;
    private Long orderId;
    private Long orderItemId;
    private String productRefSnapshot;
    private String productNameSnapshot;
    private String ruleSnapshotJson;
    private BigDecimal baseAmount;
    private BigDecimal rateSnapshot;
    private BigDecimal amount;
    private Integer observationDaysSnapshot;
    private LocalDateTime generatedAt;
    private LocalDateTime availableAt;
    private LocalDateTime settledAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer version;
}
