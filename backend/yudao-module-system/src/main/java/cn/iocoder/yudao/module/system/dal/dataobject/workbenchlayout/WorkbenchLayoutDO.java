package cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("system_workbench_layout")
@KeySequence("system_workbench_layout_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchLayoutDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String scopeType;
    private Long scopeId;
    private String draftSnapshotJson;
    private Integer draftRevision;
    private Long draftRestoredFromVersionId;
    private Long publishedVersionId;
    private Integer publishedVersionNo;
    private Boolean publishedEnabled;
    private Integer publishedPriority;

}
