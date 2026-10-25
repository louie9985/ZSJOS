package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.MenuTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.LeadTaskReminderRespVO;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MenuTaskSummaryService {
    MenuTaskSummaryRespVO getMySummary(Long userId);

    Map<Long, List<LeadTaskReminderRespVO>> getMyLeadReminders(Long userId, Collection<Long> visibleLeadIds);
    List<Long> getMyLeadReminderIds(Long userId);
    Map<Long, List<cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.StudentTaskReminderRespVO>>
            getMyStudentReminders(Long userId, Collection<Long> visibleRelationIds);
    List<Long> getMyStudentReminderIds(Long userId);
}
