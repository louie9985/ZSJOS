package cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_delivery_class_group")
public class DeliveryClassGroupDO extends TenantBaseDO {
    @TableId private Long id;
    private String name;
    private Long deptId;
    private String deptNameSnapshot;
    private LocalDate referenceDate;
    private Integer version;
}
