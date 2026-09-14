package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo; import cn.iocoder.yudao.framework.common.pojo.PageParam; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) public class GiftPurchasePageReqVO extends PageParam { private String studentName; private String orderNo; private String giftKeyword; }
