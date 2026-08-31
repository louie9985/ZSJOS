package cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("zsjos_withdrawal")
@Data
@EqualsAndHashCode(callSuper = true)
public class WithdrawalDO extends TenantBaseDO {
    @TableId private Long id;
    private String withdrawalNo;
    private Long partnerId;
    private Long applicantUserId;
    private String status;
    private String verificationStatus;
    private BigDecimal applicationAmount;
    private BigDecimal availableBalanceSnapshot;
    private String accountNameSnapshot;
    private String cardNumberSnapshot;
    private String bankNameSnapshot;
    private String branchNameSnapshot;
    private String processInstanceId;
    private LocalDateTime submittedAt;
    private BigDecimal approvedAmount;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private Long cancelledByUserId;
    private LocalDateTime cancelledAt;
    private String bankTransactionNo;
    private Long proofFileId;
    private String proofFileNameSnapshot;
    private String proofFileTypeSnapshot;
    private String payoutRemark;
    private Long paidByUserId;
    private LocalDateTime paidAt;
    private Integer version;
}
