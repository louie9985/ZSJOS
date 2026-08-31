package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_registration_item")
@KeySequence("zsjos_registration_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long registrationCaseId;
    private Long checklistItemId;
    private String itemType;
    private String itemLabelSnapshot;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private Long recordedByUserId;
}
