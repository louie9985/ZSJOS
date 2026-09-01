package cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_workbench_layout_version")
@KeySequence("system_workbench_layout_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchLayoutVersionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long layoutId;
    private String scopeType;
    private Long scopeId;
    private Integer versionNo;
    private String snapshotJson;
    private Boolean enabled;
    private Integer priority;
    private String publishRemark;
    private Long restoredFromVersionId;
    private Long publisherUserId;
    private LocalDateTime publishTime;

}
