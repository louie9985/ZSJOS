package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_registration_item_attachment")
@KeySequence("zsjos_registration_item_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationItemAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long registrationCaseId;
    private Long checklistItemId;
    private Long infraFileId;
    private String fileUrl;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Long uploadedByUserId;
    private LocalDateTime uploadedAt;
}
