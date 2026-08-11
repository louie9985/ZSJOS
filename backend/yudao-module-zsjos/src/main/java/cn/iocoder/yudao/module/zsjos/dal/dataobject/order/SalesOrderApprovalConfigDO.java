package cn.iocoder.yudao.module.zsjos.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_order_approval_config")
@KeySequence("zsjos_order_approval_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderApprovalConfigDO extends TenantBaseDO {
    @TableId private Long id;
    private Long registrationDeptId;
    private Long financeDeptId;
}
