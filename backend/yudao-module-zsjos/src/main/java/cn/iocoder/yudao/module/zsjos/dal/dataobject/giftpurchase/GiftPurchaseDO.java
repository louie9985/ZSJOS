package cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@TableName("zsjos_gift_purchase")
@KeySequence("zsjos_gift_purchase_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class GiftPurchaseDO extends TenantBaseDO {
    @TableId private Long id;
    // Null for independent manual purchases; linked order identity is never editable.
    private Long orderId;
    private String orderNo;
    private String studentName;
    private String studentMobile;
    private String studentWechatId;
    private String giftItemsJson;
    private String shippingAddress;
    private LocalDateTime generatedAt;
    private Boolean purchased;
    private Long submitterUserId;
    private String submitterUserName;
}
