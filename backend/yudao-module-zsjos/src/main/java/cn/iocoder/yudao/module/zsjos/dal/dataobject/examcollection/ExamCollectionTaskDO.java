package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_exam_collection_task", autoResultMap = true)
public class ExamCollectionTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private String title;
    private String description;
    private String status;
    private Long configVersionId;
    private String fieldsSnapshot;
    private String classIdsJson;
    private String createRequestKey;
    private String createRequestHash;
    private String tokenHash;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @lombok.ToString.Exclude
    private String tokenCiphertext;
    private Long sequenceNo;
    private Integer revision;
    private String completionReason;
    private java.time.LocalDateTime completedAt;
}
