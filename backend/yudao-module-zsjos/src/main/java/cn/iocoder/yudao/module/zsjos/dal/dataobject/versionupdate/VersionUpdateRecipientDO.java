package cn.iocoder.yudao.module.zsjos.dal.dataobject.versionupdate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_version_update_recipient")
public class VersionUpdateRecipientDO extends TenantBaseDO {
    @TableId private Long id;
    private Long updateId;
    private Long userId;
    private String userName;
    private Long deptId;
    private String deptName;
    private LocalDateTime acknowledgedTime;
}
