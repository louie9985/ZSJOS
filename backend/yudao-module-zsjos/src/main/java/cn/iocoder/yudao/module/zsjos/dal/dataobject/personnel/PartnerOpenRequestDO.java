package cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_partner_open_request")
@KeySequence("zsjos_partner_open_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerOpenRequestDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String requestNo;
    private String partnerName;
    private String partnerMobile;
    private String activeMobileKey;
    private Long assignedEmployeeUserId;
    private String assignedEmployeeNameSnapshot;
    private Long assignedEmployeeDeptIdSnapshot;
    private String assignedEmployeeDeptNameSnapshot;
    private Long applicantUserId;
    private String applicantNameSnapshot;
    private Long applicantDeptIdSnapshot;
    private String applicantDeptNameSnapshot;
    private String status;
    private String processInstanceId;
    private Long invitationId;
    private String inviteCodeSnapshot;
    private LocalDateTime inviteExpiresAt;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private String reviewReason;
    private LocalDateTime openedAt;
    private String failureReason;
    private String idempotencyKey;
    private LocalDateTime submittedAt;
    private Long cancelledByUserId;
    private LocalDateTime cancelledAt;
    @Version
    private Integer version;
}
