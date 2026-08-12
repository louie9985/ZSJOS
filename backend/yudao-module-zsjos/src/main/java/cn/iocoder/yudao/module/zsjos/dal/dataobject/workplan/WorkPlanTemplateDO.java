package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_plan_template")
@KeySequence("zsjos_work_plan_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanTemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private Long typeId;
    private String code;
    private String name;
    private String description;
    private String status;
    private Integer currentVersionNo;
}
