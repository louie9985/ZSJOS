package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_student_contact_config_version")
@KeySequence("zsjos_student_contact_config_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentContactConfigVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Integer versionNo;
    private String status;
    private Integer firstContactTimeoutMinutes;
    private Integer studyPlanTimeoutMinutes;
    private String checklistJson;
    private String quickNotesJson;
    private String collaboratorTabsJson;
    private Integer version;
}
