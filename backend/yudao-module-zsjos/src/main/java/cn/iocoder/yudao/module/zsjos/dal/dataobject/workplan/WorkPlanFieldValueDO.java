package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("zsjos_work_field_value")
@KeySequence("zsjos_work_field_value_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanFieldValueDO extends TenantBaseDO {
    @TableId private Long id;
    private Long fieldDefinitionId;
    private String subjectType;
    private Long subjectId;
    private String valueText;
    private BigDecimal valueDecimal;
    private LocalDateTime valueDatetime;
    private Long valueRefId;
    private String valueJson;
}
