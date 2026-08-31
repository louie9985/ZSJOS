package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_sales_dispatch_preference")
@KeySequence("zsjos_sales_dispatch_preference_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesDispatchPreferenceDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Boolean acceptingEnabled;
}
