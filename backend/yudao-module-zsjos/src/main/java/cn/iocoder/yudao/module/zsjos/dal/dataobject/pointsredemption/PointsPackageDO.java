package cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("zsjos_points_package")
public class PointsPackageDO extends TenantBaseDO {
    @TableId private Long id;
    private String name;
    private Integer creditsAmount;
    private Integer validDays;
    private Boolean enabled;
}
