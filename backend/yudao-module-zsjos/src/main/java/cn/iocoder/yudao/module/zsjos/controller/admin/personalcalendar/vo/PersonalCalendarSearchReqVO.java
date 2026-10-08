package cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarSearchReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalCalendarSearchReqVO extends CalendarSearchReqVO {
    private String readScope;
    private Long targetUserId;
}

