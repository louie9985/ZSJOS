package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_plan_type")
@KeySequence("zsjos_work_plan_type_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanTypeDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer sort;
}
