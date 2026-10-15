package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendarrestday;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_calendar_rest_day")
public class CalendarRestDayDO extends TenantBaseDO {
    private Long id;
    private LocalDate date;
    private Boolean rest;
    private Long version;
}
