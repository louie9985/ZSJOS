package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_plan_field_definition")
@KeySequence("zsjos_work_plan_field_definition_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanFieldDefinitionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long planId;
    private Long templateFieldId;
    private String fieldKey;
    private String label;
    private String section;
    private String fieldType;
    private Boolean required;
    private String unit;
    private String placeholder;
    private Boolean filterable;
    private Boolean exportable;
    private String optionsJson;
    private String defaultValueJson;
    private String origin;
    private Integer sort;
}
