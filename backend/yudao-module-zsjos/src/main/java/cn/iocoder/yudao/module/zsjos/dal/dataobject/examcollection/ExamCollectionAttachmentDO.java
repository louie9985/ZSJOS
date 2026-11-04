package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_exam_collection_attachment", autoResultMap = true)
public class ExamCollectionAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Long submissionId;
    private String fieldKey;
    private String side;
    private Long infraFileId;
    private Long fileSize;
    private String contentType;
    private String originalName;
    private String requestKey, requestHash;
}
