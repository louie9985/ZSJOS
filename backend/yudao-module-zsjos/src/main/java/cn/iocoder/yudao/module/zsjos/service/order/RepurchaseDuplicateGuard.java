package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import java.math.BigDecimal;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_REPURCHASE_DUPLICATE;

@Service
public class RepurchaseDuplicateGuard {
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderItemMapper itemMapper;

    /** Called under the Person row lock, after idempotent replay and ordinary input validation. */
    public void check(Long personId, SalesOrderSubmitReqVO req) {
        var orders = orderMapper.selectByPersonId(personId).stream()
                .filter(o -> Set.of("effective", "pending_approval", "revision_required").contains(o.getStatus())).toList();
        Set<Long> files = new HashSet<>();
        if (req.getPaymentVouchers() != null) req.getPaymentVouchers().forEach(v -> files.add(v.getInfraFileId()));
        files.remove(null);
        for (var order : orders) {
            if (!files.isEmpty() && order.getPaymentVoucherRefs() != null) {
                var refs = JsonUtils.parseArray(order.getPaymentVoucherRefs(), Object.class);
                if (refs.stream().filter(Map.class::isInstance).map(Map.class::cast)
                        .map(m -> m.get("infraFileId")).filter(Number.class::isInstance)
                        .anyMatch(id -> files.contains(((Number) id).longValue()))) throw exception(SALES_ORDER_REPURCHASE_DUPLICATE);
            }
        }
        // Same course alone can be a real repeat purchase. Match the payment time and amounts as well.
        var sameTime = orders.stream().filter(o -> Objects.equals(o.getCustomerPaidAt(), req.getCustomerPaidAt())).toList();
        if (sameTime.isEmpty()) return;
        var items = itemMapper.selectListByOrderIds(sameTime.stream().map(SalesOrderDO::getId).toList());
        List<String> requested = req.getItems().stream().map(i -> key(i.getSpuRef(), i.getSkuRef(), i.getActualAmount())).sorted().toList();
        for (var order : sameTime) {
            var recorded = items.stream().filter(i -> order.getId().equals(i.getOrderId()))
                    .map(i -> key(i.getProductRef(), i.getSkuRef(), i.getPayableAmount())).sorted().toList();
            if (requested.equals(recorded)) throw exception(SALES_ORDER_REPURCHASE_DUPLICATE);
        }
    }
    private String key(String product, String sku, BigDecimal amount) {
        return product + ":" + sku + ":" + (amount == null ? "unknown" : amount.stripTrailingZeros().toPlainString());
    }
}
