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
}
