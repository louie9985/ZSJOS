package cn.iocoder.yudao.module.zsjos.dal.dataobject.performance;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_performance_attribution")
public class PerformanceAttributionDO extends TenantBaseDO {
 @TableId private Long id;
 private String outcome;
 private java.time.LocalDateTime completedAt;
 private String factType;
 private Long factId;
 private Long userId;
 private String userName;
 private Long deptId;
 private String deptName;
 private Long centerId;
 private String centerName;
 private Long leadId;
 private Long assignmentId;
 private java.time.LocalDateTime receivedAt;
 private String sourceGroup;
 private String channelCode;
 private String channelLabel;
}
