package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_exam_collection_event", autoResultMap = true)
public class ExamCollectionEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Long submissionId;
    private Long operatorUserId;
    private String action;
    private String reason;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @lombok.ToString.Exclude
    private String detailsJson;
}
