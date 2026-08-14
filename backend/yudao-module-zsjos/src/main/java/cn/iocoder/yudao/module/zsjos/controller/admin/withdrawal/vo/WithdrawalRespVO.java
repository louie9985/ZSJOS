package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class WithdrawalRespVO {
    private Long id;
    private String withdrawalNo;
    private Long applicantUserId;
    private String status;
    private String verificationStatus;
    private BigDecimal applicationAmount;
    private BigDecimal availableBalanceSnapshot;
    private String accountNameSnapshot;
    private String maskedCardNumber;
    private String cardNumber;
    private String bankNameSnapshot;
    private String branchNameSnapshot;
    private String processInstanceId;
    private LocalDateTime submittedAt;
    private BigDecimal approvedAmount;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private String bankTransactionNo;
    private Long proofFileId;
    private String proofUrl;
    private String payoutRemark;
    private Long paidByUserId;
    private LocalDateTime paidAt;
    private List<Item> items;

    @Data
    public static class Item {
        private Long cashbackId;
        private BigDecimal amount;
    }
}
