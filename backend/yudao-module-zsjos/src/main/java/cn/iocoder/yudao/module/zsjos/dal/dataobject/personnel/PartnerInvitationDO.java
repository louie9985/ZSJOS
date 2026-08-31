package cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_partner_invitation")
@KeySequence("zsjos_partner_invitation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerInvitationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String inviteCode;
    private String name;
    private String mobile;
    private Long assignedOperatorUserId;
    private String assignedOperatorNameSnapshot;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime voidedAt;
    private Long partnerId;
    private Long createdByUserId;
    @Version
    private Integer version;
}
