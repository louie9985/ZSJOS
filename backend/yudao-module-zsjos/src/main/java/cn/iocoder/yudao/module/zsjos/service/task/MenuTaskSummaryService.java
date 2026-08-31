package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.MenuTaskSummaryRespVO;

public interface MenuTaskSummaryService {
    MenuTaskSummaryRespVO getMySummary(Long userId);
}
