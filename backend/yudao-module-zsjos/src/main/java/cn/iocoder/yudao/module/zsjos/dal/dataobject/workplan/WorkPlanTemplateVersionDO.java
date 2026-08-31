package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_work_plan_template_version")
@KeySequence("zsjos_work_plan_template_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanTemplateVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long templateId;
    private Integer versionNo;
    private String status;
    private String periodMode;
    private LocalDateTime publishedAt;
}
