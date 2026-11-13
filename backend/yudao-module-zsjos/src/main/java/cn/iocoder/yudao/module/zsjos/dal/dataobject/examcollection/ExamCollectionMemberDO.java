package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_exam_collection_member", autoResultMap = true)
public class ExamCollectionMemberDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Long serviceRelationId;
    private Long personId;
    private Long classId;
    private Long plannerUserId;
    private String classNameSnapshot;
    private String identityHash;
    private Boolean active;
    private Long lastSubmissionId;
}
