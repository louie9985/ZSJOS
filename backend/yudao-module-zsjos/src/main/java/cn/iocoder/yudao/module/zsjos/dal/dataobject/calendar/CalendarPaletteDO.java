package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
@TableName("zsjos_calendar_palette")
public class CalendarPaletteDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String calendarType;
    private String colorsJson;
}
