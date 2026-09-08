package cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_class_transfer_request")
@KeySequence("zsjos_class_transfer_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassTransferRequestDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId;
    private Integer serviceRelationVersion;
    private Long fromClassId;
    private String fromClassNoSnapshot;
    private String fromClassNameSnapshot;
    private Long targetClassId;
    private String targetClassNoSnapshot;
    private String targetClassNameSnapshot;
    private Long fromHomeroomUserId;
    private String fromHomeroomUserNameSnapshot;
    private Long targetHomeroomUserId;
    private String targetHomeroomUserNameSnapshot;
    private Long applicantUserId;
    private Long reviewerUserId;
    private String reason;
    private String status;
    private String processInstanceId;
    private LocalDateTime submittedAt;
    private LocalDateTime finishedAt;
    private String resolutionReason;
    private Integer version;
}
