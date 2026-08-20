package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_registration_checklist_template_item")
@KeySequence("zsjos_registration_checklist_template_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationChecklistTemplateItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long versionId;
    private String itemKey;
    private String itemType;
    private String title;
    private Integer sort;
    private Boolean enabled;
    private Boolean systemRequired;
    private Boolean attachmentRequired;
}
