package cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CashbackPageReqVO extends PageParam {
    private String type;
    private String status;
    private String keyword;
    private Long beneficiaryUserId;
    private Long partnerId;
    @jakarta.validation.constraints.DecimalMin("0")
    private java.math.BigDecimal amountMin;
    @jakarta.validation.constraints.DecimalMin("0")
    private java.math.BigDecimal amountMax;
    @jakarta.validation.Valid
    private cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO advancedFilter;
    private String orderNo;
    private String productName;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime generatedAtFrom;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime generatedAtTo;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime availableAtFrom;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime availableAtTo;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime settledAtFrom;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime settledAtTo;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "金额或时间区间的起点不能大于终点")
    public boolean isRangeValid() {
        return (amountMin == null || amountMax == null || amountMin.compareTo(amountMax) <= 0)
                && (generatedAtFrom == null || generatedAtTo == null || !generatedAtFrom.isAfter(generatedAtTo))
                && (availableAtFrom == null || availableAtTo == null || !availableAtFrom.isAfter(availableAtTo))
                && (settledAtFrom == null || settledAtTo == null || !settledAtFrom.isAfter(settledAtTo))
                ;
    }

}
