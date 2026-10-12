package cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("zsjos_points_request")
public class PointsRequestDO extends TenantBaseDO {
    @TableId private Long id;
    private String requestNo;
    private String submissionKey;
    private Long applicantUserId;
    private String applicantName;
    private Long packageId;
    private String packageName;
    private Integer creditsAmount;
    private Integer validDays;
    private String reason;
    private String attachmentsJson;
    private String approvalStatus;
    private String issueStatus;
    private String processInstanceId;
    private String processDefinitionId;
    private String taskKeysJson;
    private String approverIdsJson;
    private String orderId;
    private LocalDateTime expiresAt;
    private String platformUrl;
    private String batchId;
    private String externalCodeId;
    @ToString.Exclude
    private String codeCiphertext;
    private String codeStatus;
    private LocalDateTime redeemedAt;
    private LocalDateTime syncedAt;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private Boolean notified;
    private LocalDateTime submittedAt;
}
