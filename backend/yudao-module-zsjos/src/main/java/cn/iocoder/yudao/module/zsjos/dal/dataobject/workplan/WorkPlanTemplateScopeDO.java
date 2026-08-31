package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_plan_template_scope")
@KeySequence("zsjos_work_plan_template_scope_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanTemplateScopeDO extends TenantBaseDO {
    @TableId private Long id;
    private Long templateId;
    private Long deptId;
    private Boolean includeChildren;
}
