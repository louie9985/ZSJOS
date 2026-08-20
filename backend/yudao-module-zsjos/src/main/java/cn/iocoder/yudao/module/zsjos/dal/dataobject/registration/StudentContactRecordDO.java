package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_student_contact_record")
@KeySequence("zsjos_student_contact_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentContactRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId;
    private Long taskId;
    private String contactType;
    private Boolean successful;
    private String unsuccessfulReasonValue;
    private String unsuccessfulReasonLabelSnapshot;
    private String remark;
    private String attachmentFileIdsJson;
    private String checklistResultJson;
    private LocalDateTime nextContactAt;
    private Long operatorUserId;
    private LocalDateTime submittedAt;
    private String idempotencyKey;
    private String requestFingerprint;
}
