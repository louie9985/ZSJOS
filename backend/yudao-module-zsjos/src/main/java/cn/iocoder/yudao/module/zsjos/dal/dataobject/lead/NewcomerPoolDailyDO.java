package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_newcomer_pool_daily")
public class NewcomerPoolDailyDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private LocalDate businessDate;
    private Integer acceptedCount;
}
