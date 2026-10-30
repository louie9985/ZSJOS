package cn.iocoder.yudao.module.zsjos.dal.dataobject.homemetric;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_home_metric_version")
public class HomeMetricVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long metricId;
    private Integer version;
    private String specJson;
}
