package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_plan_template_task")
@KeySequence("zsjos_work_plan_template_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanTemplateItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long templateVersionId;
    private String title;
    private String description;
    private String deliverableRequirement;
    private Integer dueOffsetDays;
    private String dueOffsetBasis;
    private Boolean confirmationRequired;
    private Integer sort;
}
