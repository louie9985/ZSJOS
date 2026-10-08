package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import java.util.Map;
import java.util.stream.Collectors;

public final class FinanceListSort {
    private FinanceListSort() {}
    public static BusinessListSort<CashbackRespVO> cashback(AdvancedFilterCatalogRespVO catalog) {
        var types = labels(catalog, "cashback.type"); var statuses = labels(catalog, "cashback.status");
        return new BusinessListSort<CashbackRespVO>(CashbackRespVO::getId)
                .field("cashbackNo", CashbackRespVO::getCashbackNo)
                .field("beneficiaryName", row -> missing(row.getBeneficiaryName()))
                .field("partnerName", CashbackRespVO::getPartnerName)
                .field("type", row -> types.get(row.getType()))
                .field("customer", row -> row.getSource() == null ? null : BusinessListSort.first(row.getSource().getStudentName(), row.getSource().getCustomerName()))
                .field("source", row -> row.getSource() == null ? null : BusinessListSort.first(row.getSource().getOrderNo(), row.getSource().getLeadNo()))
                .field("productNameSnapshot", CashbackRespVO::getProductNameSnapshot)
                .field("baseAmount", row -> "valid".equals(row.getType()) ? null : row.getBaseAmount())
                .field("rateSnapshot", row -> "valid".equals(row.getType()) ? null : row.getRateSnapshot())
                .field("amount", CashbackRespVO::getAmount)
                .field("status", row -> statuses.get(row.getStatus()))
                .field("generatedAt", CashbackRespVO::getGeneratedAt)
                .field("availableAt", CashbackRespVO::getAvailableAt);
    }
    public static BusinessListSort<WithdrawalRespVO> withdrawal() {
        return new BusinessListSort<WithdrawalRespVO>(WithdrawalRespVO::getId)
                .field("withdrawalNo", WithdrawalRespVO::getWithdrawalNo)
                .field("applicantName", row -> missing(row.getApplicantName()))
                .field("partnerName", WithdrawalRespVO::getPartnerName)
                .field("cashbackCount", WithdrawalRespVO::getCashbackCount)
                .field("applicationAmount", WithdrawalRespVO::getApplicationAmount)
                .field("status", row -> row.getStatus() == null ? null : cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.statusLabel(row.getStatus()))
                .field("accountNameSnapshot", WithdrawalRespVO::getAccountNameSnapshot)
                .field("cardNumber", row -> BusinessListSort.first(row.getCardNumber(), row.getMaskedCardNumber()))
                .field("bankNameSnapshot", WithdrawalRespVO::getBankNameSnapshot)
                .field("submittedAt", WithdrawalRespVO::getSubmittedAt)
                .field("reviewedAt", WithdrawalRespVO::getReviewedAt);
    }
    private static String missing(String value) { return value != null && value.startsWith("历史") && value.endsWith("缺失") ? null : value; }
    private static Map<String,String> labels(AdvancedFilterCatalogRespVO catalog, String field) {
        if (catalog == null) return new java.util.HashMap<>();
        return catalog.fields().stream().filter(f -> f.fieldKey().equals(field)).flatMap(f -> f.options().stream())
                .collect(Collectors.toMap(AdvancedFilterCatalogRespVO.OptionVO::value, AdvancedFilterCatalogRespVO.OptionVO::label));
    }
}
