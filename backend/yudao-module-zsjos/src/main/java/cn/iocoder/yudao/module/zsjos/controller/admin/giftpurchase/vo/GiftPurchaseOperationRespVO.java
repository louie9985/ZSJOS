package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GiftPurchaseOperationRespVO {
    private String action;
    private String operatorName;
    private LocalDateTime occurredAt;
}
