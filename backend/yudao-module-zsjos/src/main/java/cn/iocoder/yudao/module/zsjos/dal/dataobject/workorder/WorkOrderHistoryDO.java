package cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_work_order_history")
@KeySequence("zsjos_work_order_history_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderHistoryDO extends TenantBaseDO {
    @TableId private Long id;
    private Long workOrderId;
    private String fromStatus;
    private String toStatus;
    private Long operatorUserId;
    private String reason;
    private LocalDateTime operatedAt;
    private String idempotencyKey;
    private String operation;
    private String requestFingerprint;
    private Integer roundNo;
    private String resultRemark;
    private String attachmentIdsJson;
}
