package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_attachment")
@KeySequence("zsjos_work_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private String subjectType;
    private Long subjectId;
    private Long infraFileId;
    private Integer sort;
}
