package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarSearchReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MediaCalendarSearchReqVO extends CalendarSearchReqVO {
    private String currentStatusValue;
    private String stageValue;
    private Long directorUserId;
    private Long operatorUserId;
}

