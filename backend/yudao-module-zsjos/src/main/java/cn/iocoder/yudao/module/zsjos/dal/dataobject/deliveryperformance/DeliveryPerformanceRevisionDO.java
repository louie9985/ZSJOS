package cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryperformance;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_performance_revision")
public class DeliveryPerformanceRevisionDO extends TenantBaseDO {
 @TableId private Long id;
 private Long targetId;
 private String beforeJson;
 private String afterJson;
 private String reason;
 private Long operatorId;
}
