package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Accessors(chain = true)
public class PurchaseIntentRespVO {
    private Long id;
    private String purchaseIntentNo;
    private String collectionMode;
    private String purchaseType;
    private Long leadId;
    private Long personId;
    private Map<String, Object> draft;
    private String itemSnapshotJson;
    private BigDecimal totalAmount;
    private String currency;
    private Integer version;
    private String displayStatus;
    private Long paymentIntentId;
    private String paymentIntentNo;
    private String paymentUrl;
    private String paymentStatus;
    private LocalDateTime paymentExpiresAt;
    private Boolean paymentLocked;
    /** 已发起取消但通联关单结果未确认；为 true 时金额仍锁定且不允许生成新链接 */
    private Boolean paymentCancelPending;
    private String paymentCancelMessage;
}
