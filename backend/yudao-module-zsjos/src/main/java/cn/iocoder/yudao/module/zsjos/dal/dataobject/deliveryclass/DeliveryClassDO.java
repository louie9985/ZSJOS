package cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@TableName("zsjos_delivery_class")
@KeySequence("zsjos_delivery_class_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DeliveryClassDO extends TenantBaseDO {
    @TableId private Long id;
    private String classNo;
    private String className;
    private Boolean systemClass;
    private Long productId;
    private String productNameSnapshot;
    private String selectedAttrsJson;
    private String selectedSpecsJson;
    private String selectedSkusJson;
    private Long categoryId;
    private String categoryNameSnapshot;
    private String categoryPathSnapshot;
    private Long examScheduleId;
    private String examScheduleSnapshot;
    private Long homeroomUserId;
    private String homeroomUserNameSnapshot;
    private Long deptId;
    private String deptNameSnapshot;
    private String status;
    private Integer version;
}
