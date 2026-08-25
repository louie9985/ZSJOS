package cn.iocoder.yudao.module.zsjos.dal.dataobject.director;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@TableName("zsjos_director_config")
@KeySequence("zsjos_director_config_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DirectorConfigDO extends TenantBaseDO {
    @TableId private Long id;
    private Integer interviewAppointmentHours;
    private Integer positioningDueHours;
    private Integer trialDays;
    private Integer version;
}
