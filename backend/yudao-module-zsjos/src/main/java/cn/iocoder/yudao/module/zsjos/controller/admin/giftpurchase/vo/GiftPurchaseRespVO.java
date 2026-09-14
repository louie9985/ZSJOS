package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo; import lombok.*; import java.time.LocalDateTime;
@Data public class GiftPurchaseRespVO { private Long id; private String orderNo; private String studentName; private String studentMobile; private String studentWechatId; private String giftItemsJson; private String shippingAddress; private LocalDateTime generatedAt; }
