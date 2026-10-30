package cn.iocoder.yudao.module.zsjos.dal.dataobject.homemetric;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_home_metric")
public class HomeMetricDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String draftJson;
    private Integer version;
    private Integer publishedVersion;
    private Boolean enabled;
}
