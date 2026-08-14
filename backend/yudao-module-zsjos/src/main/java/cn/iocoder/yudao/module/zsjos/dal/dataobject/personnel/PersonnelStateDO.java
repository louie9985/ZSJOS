package cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_personnel_state")
@KeySequence("zsjos_personnel_state_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelStateDO extends TenantBaseDO {
    @TableId private Long id;
    private Long systemUserId;
    private String businessState;
    private String changeReason;
    private Long changedByUserId;
    private LocalDateTime changedAt;
    private Integer version;
}
