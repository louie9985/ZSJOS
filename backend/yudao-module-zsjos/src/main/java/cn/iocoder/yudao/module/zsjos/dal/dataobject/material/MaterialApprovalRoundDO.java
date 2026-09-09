package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_material_approval_round")
@KeySequence("zsjos_material_approval_round_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialApprovalRoundDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialVersionId;
    private Integer roundNo;
    private String status;
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private String businessKey;
    private String lastEventKey;
    private String resultReason;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private LocalDateTime concludedAt;
}
