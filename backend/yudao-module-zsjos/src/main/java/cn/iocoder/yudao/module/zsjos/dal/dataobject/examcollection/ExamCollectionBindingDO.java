package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_exam_collection_binding", autoResultMap = true)
public class ExamCollectionBindingDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private String identityHash;
    private Long serviceRelationId;
    private Boolean manual;
    private Long lastSubmissionId;
    private Long lastSequenceNo;
    private Integer revision;
}
