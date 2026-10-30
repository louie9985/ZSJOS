package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data
public class GiftPurchaseBatchReqVO {
    @NotEmpty @Size(max = 200)
    private List<@NotNull @Positive Long> ids;
}
