package cn.iocoder.yudao.module.zsjos.dal.dataobject.performance;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_performance_target")
public class PerformanceTargetDO extends TenantBaseDO {
 @TableId private Long id;
 private String scopeType;
 private String businessDomain;
 private Long mergedDeliveryTargetId;
 @TableField(exist=false) private String sourceToken;
 /** Read-only marker: merged sources disagree (dual-domain organization period or stale merge link); never persisted. */
 @TableField(exist=false) private Boolean sourceConflict;
 private Long scopeId;
 private Long deptId;
 private Long centerId;
 private String periodType;
 private java.time.LocalDate periodStart;
 private java.math.BigDecimal floorAmount;
 private java.math.BigDecimal sprintAmount;
 private Boolean manual;
 private String reason;
 private Integer version;
}
