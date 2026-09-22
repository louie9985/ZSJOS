package cn.iocoder.yudao.module.zsjos.dal.dataobject.performance;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_performance_org")
public class PerformanceOrgDO extends TenantBaseDO {
 @TableId private Long id;
 private Long deptId;
 private Long centerId;
 private String kind;
 private Integer version;
}
