package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_registration_checklist_template")
@KeySequence("zsjos_registration_checklist_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationChecklistTemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private String name;
    private Long publishedVersionId;
    private Long draftVersionId;
    private Integer version;
}
