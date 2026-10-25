package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderGiftSnapshot;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GiftPurchaseRespVO {
    private Long id;
    private Long orderId;
    private String orderNo;
    private String studentName;
    private String studentMobile;
    private String studentWechatId;
    private String giftItemsJson;
    private List<SalesOrderGiftSnapshot> giftItemSnapshots;
    private boolean giftItemsInvalid;
    private String shippingAddress;
    private LocalDateTime generatedAt;
    private boolean purchased;
    private Long submitterUserId;
    private String submitterUserName;
    @Schema(description = "采购来源：ORDER 订单提交，MANUAL 手工创建，UNKNOWN 来源待确认")
    private String sourceType;
    @Schema(description = "来源人：订单提交人或手工创建人，非最近编辑人")
    private Long sourceUserId;
    @Schema(description = "来源人历史姓名，缺失不回查当前姓名")
    private String sourceUserName;
    @Schema(description = "当前已采购状态的标记人，改回未采购后为空")
    private Long purchasedByUserId;
    @Schema(description = "标记已采购人的操作时姓名，普通编辑不覆盖")
    private String purchasedByName;
    @Schema(description = "当前最近一次标记已采购时间，未采购时为空")
    private LocalDateTime purchasedAt;
    private String operatorName;
    private LocalDateTime operatedAt;
}
