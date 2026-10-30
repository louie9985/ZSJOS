package cn.iocoder.yudao.module.zsjos.service.giftpurchase;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.springframework.stereotype.Service;
@Service
public class GiftPurchaseBatchItemService {
    @ZsjosPermission(bizType = "gift-purchase", bizId = "#id", action = "batch-purchase")
    public void check(Long id) {
        // Separate proxied boundary makes the batch's per-object AOP checks effective.
    }
}
