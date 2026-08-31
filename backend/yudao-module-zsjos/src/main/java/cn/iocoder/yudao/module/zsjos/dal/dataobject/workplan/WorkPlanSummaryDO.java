package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_work_plan_summary")
@KeySequence("zsjos_work_plan_summary_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanSummaryDO extends TenantBaseDO {
    @TableId private Long id;
    private Long planId;
    private String summary;
    private Long submitterUserId;
    private LocalDateTime submittedAt;
}
