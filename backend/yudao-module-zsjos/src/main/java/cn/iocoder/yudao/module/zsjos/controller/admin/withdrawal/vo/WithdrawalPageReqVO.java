package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WithdrawalPageReqVO extends PageParam {
    @jakarta.validation.constraints.Pattern(regexp = "SELF|ALL|USER")
    private String readScope;
    private Long targetUserId;
    private String status;
    private String keyword;
    private Long applicantUserId;
    private Long partnerId;
    @jakarta.validation.constraints.DecimalMin("0")
    private java.math.BigDecimal amountMin;
    @jakarta.validation.constraints.DecimalMin("0")
    private java.math.BigDecimal amountMax;
    @jakarta.validation.Valid
    private cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO advancedFilter;
    private String withdrawalNo;
    private String bankTransactionNo;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime submittedAtFrom;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime submittedAtTo;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime reviewedAtFrom;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime reviewedAtTo;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime paidAtFrom;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime paidAtTo;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "金额或时间区间的起点不能大于终点")
    public boolean isRangeValid() {
        return (amountMin == null || amountMax == null || amountMin.compareTo(amountMax) <= 0)
                && (submittedAtFrom == null || submittedAtTo == null || !submittedAtFrom.isAfter(submittedAtTo))
                && (reviewedAtFrom == null || reviewedAtTo == null || !reviewedAtFrom.isAfter(reviewedAtTo))
                && (paidAtFrom == null || paidAtTo == null || !paidAtFrom.isAfter(paidAtTo))
                ;
    }

}
