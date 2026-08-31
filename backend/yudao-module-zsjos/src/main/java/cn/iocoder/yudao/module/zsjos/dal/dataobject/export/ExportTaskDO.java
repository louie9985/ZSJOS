package cn.iocoder.yudao.module.zsjos.dal.dataobject.export;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_export_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class ExportTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private String taskNo;
    private String exportType;
    private String status;
    private Long creatorUserId;
    private String creatorNameSnapshot;
    private String creatorRoleSnapshot;
    private String filterJson;
    private String permissionSnapshotJson;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseExpiresAt;
    private Long resultFileId;
    private String resultFileName;
    private Long resultFileSize;
    private LocalDateTime readyAt;
    private LocalDateTime expiresAt;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime cancelledAt;
    private LocalDateTime lastActiveAt;
    private Integer version;
}
