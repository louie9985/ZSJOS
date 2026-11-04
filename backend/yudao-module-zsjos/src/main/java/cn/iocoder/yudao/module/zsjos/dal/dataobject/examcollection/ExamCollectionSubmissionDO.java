package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_exam_collection_submission", autoResultMap = true)
public class ExamCollectionSubmissionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private String sessionTokenHash;
    private String idempotencyKey;
    private String requestHash;
    private Long sequenceNo;
    private String status;
    private String identityHash;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @lombok.ToString.Exclude
    private String answersJson;
    private String attachmentIdsJson;
    private Long serviceRelationId;
    private String exceptionCode;
    private java.time.LocalDateTime submittedAt;
    private java.time.LocalDateTime expiresAt;
    private Integer revision;
}
