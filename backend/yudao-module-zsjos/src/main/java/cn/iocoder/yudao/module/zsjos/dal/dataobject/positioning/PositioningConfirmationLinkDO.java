package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("zsjos_positioning_confirmation_link")
@KeySequence("zsjos_positioning_confirmation_link_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PositioningConfirmationLinkDO extends TenantBaseDO {
    @TableId private Long id;
    private Long cardId;
    private Long submissionId;
    private String tokenHash;
    private String status;
    private Long createdByUserId;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime usedAt;
    private Integer version;
}
