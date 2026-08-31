package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PartnerWithdrawalRespVO {
    private Long id;
    private String withdrawalNo;
    private String status;
    private BigDecimal applicationAmount;
    private String accountNameSnapshot;
    private String maskedCardNumber;
    private String bankNameSnapshot;
    private String branchNameSnapshot;
    private LocalDateTime submittedAt;
    private BigDecimal approvedAmount;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private LocalDateTime paidAt;
}
