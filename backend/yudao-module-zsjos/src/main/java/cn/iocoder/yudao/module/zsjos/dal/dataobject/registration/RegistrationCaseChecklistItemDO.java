package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_registration_case_checklist_item")
@KeySequence("zsjos_registration_case_checklist_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationCaseChecklistItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long registrationCaseId;
    private Long templateItemId;
    private String itemKey;
    private String itemType;
    private String titleSnapshot;
    private Integer sort;
    private Boolean attachmentRequired;
    private Boolean checked;
    private Long checkedByUserId;
    private LocalDateTime checkedAt;
    private Integer version;
}
