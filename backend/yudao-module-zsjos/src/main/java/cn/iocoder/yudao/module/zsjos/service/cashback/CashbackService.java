package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackRespVO;
import java.math.BigDecimal;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.CashbackSummaryRespVO;

public interface CashbackService {
    Long ensureValidCashback(Long leadId);
    Long ensureDealCashback(DealCashbackCommand command);
    boolean isEligibleDealLead(Long leadId);
    BigDecimal resolveDealRate(String productRef);
    void cancelDealCashbacks(Long orderId, String reason);
    BigDecimal getOrderCashbackTotal(Long orderId, Long beneficiaryUserId);
    int settleMatured();
    void assertOrderRejectable(Long orderId);
    PageResult<CashbackRespVO> getPage(CashbackPageReqVO request, Long beneficiaryUserId);
    CashbackSummaryRespVO getMySummary(Long userId);

    record DealCashbackCommand(Long leadId, Long orderId, Long orderItemId, String productRef,
                               String productName, BigDecimal actualAmount, BigDecimal rateSnapshot) {}
}
