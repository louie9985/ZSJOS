package cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_order_number_counter")
@KeySequence("zsjos_work_order_number_counter_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderNumberCounterDO extends TenantBaseDO {
    @TableId private Long id;
    private String numberPrefix;
    private String resetKey;
    private Long currentValue;
}
