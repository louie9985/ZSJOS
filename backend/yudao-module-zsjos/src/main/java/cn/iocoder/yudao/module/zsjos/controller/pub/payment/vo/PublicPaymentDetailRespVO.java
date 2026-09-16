package cn.iocoder.yudao.module.zsjos.controller.pub.payment.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;

@Data
@Accessors(chain = true)
public class PublicPaymentDetailRespVO {
    private String paymentIntentNo;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private LocalDateTime expiresAt;
    private List<Item> items;

    public record Item(String productName, String skuName, BigDecimal actualAmount, List<ProductSpecVO> specs) {
    }
}
