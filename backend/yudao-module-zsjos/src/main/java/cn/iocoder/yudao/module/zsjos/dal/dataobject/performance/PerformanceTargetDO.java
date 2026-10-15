package cn.iocoder.yudao.module.zsjos.dal.dataobject.performance;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_performance_target")
public class PerformanceTargetDO extends TenantBaseDO {
 @TableId private Long id;
 private String scopeType;
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
