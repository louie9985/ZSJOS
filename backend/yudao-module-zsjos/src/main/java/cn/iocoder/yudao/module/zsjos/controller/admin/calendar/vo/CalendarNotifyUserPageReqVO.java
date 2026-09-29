package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CalendarNotifyUserPageReqVO extends PageParam {
    @NotBlank private String calendarType;
    @Size(max = 100) private String keyword;
}
