package cn.iocoder.yudao.module.zsjos.controller.admin.task.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BusinessTaskSummaryRespVO {
    private long unscheduled;
    private long overdue;
    private long today;
    private long future;
}
