package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@TableName("zsjos_person_no_daily_counter")
@KeySequence("zsjos_person_no_daily_counter_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonNoDailyCounterDO extends TenantBaseDO {
    @TableId private Long id;
    private LocalDate sequenceDate;
    private Integer currentValue;
}
