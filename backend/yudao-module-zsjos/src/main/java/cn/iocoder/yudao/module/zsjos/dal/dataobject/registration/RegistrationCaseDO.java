package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_registration_case")
@KeySequence("zsjos_registration_case_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationCaseDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private String status;
    private Long checklistVersionId;
    private Long studyPlannerUserId;
    private LocalDateTime registrationApprovedAt;
    private Long completedByUserId;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer version;
}
