package cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@TableName("zsjos_gift_purchase_operation")
@Data @EqualsAndHashCode(callSuper = true)
public class GiftPurchaseOperationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long purchaseId;
    private String action;
    private Long operatorUserId;
    private String operatorName;
    private LocalDateTime occurredAt;
}
