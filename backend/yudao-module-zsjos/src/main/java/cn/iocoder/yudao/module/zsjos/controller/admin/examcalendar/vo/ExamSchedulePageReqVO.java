package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExamSchedulePageReqVO extends PageParam {
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    private Long categoryId;
    private String displayStatus;
}
