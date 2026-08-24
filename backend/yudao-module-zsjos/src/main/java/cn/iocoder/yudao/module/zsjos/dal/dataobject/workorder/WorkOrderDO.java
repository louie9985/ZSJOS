package cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_work_order")
@KeySequence("zsjos_work_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderDO extends TenantBaseDO {
    @TableId private Long id;
    private String orderNo;
    private String sceneCode;
    private String sceneNameSnapshot;
    private String assignmentMode;
    private Long sourceUserId;
    private Long targetUserId;
    private String sourceNameSnapshot;
    private String targetNameSnapshot;
    private String status;
    private String fieldSnapshotJson;
    private String valueJson;
    private String attachmentIdsJson;
    private String idempotencyKey;
    private Long commandUserId;
    private String requestFingerprint;
    private String returnReason;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
    private LocalDateTime acceptedAt;
    @Version private Integer version;
}
