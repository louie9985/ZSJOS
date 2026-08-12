package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("zsjos_work_plan")
@KeySequence("zsjos_work_plan_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanDO extends TenantBaseDO {
    @TableId private Long id;
    private String title;
    private String periodType;
    private Long planTypeId;
    private Long templateId;
    private Long templateVersionId;
    private Long ownerUserId;
    private Long ownerDeptId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String objective;
    private String keyRequirements;
    private String status;
    private Long creatorUserId;
    private LocalDateTime publishedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer version;
}
