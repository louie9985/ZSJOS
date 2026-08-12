package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessTaskServiceImplTest {
    @InjectMocks private BusinessTaskServiceImpl service;
    @Mock private BusinessTaskMapper taskMapper;
    @Mock private LeadMapper leadMapper;

    @Test
    void excludesInvalidLeadLifecycleTasksFromSummaryAndPage() {
        LocalDateTime overdueAt = LocalDateTime.now().minusDays(1);
        BusinessTaskDO staleReminder = task(14L, "lead_follow_up_reminder", 8L, overdueAt);
        BusinessTaskDO activeFirstFollowUp = task(2L, "lead_first_follow_up", 6L, overdueAt);
        when(taskMapper.selectMyPending(231L)).thenReturn(List.of(staleReminder, activeFirstFollowUp));
        when(leadMapper.selectBatchIds(List.of(8L, 6L))).thenReturn(List.of(
                lead(8L, "invalid"), lead(6L, "submitted")));

        BusinessTaskSummaryRespVO summary = service.getMySummary(231L);
        PageResult<BusinessTaskRespVO> page = service.getMyPage(231L, "overdue", 1, 20);

        assertEquals(1L, summary.getOverdue());
        assertEquals(1L, page.getTotal());
        assertEquals(2L, page.getList().getFirst().getId());
    }

    private BusinessTaskDO task(Long id, String taskType, Long leadId, LocalDateTime dueAt) {
        BusinessTaskDO task = new BusinessTaskDO();
        task.setId(id); task.setTaskType(taskType); task.setBizType("lead");
        task.setBizId(leadId); task.setStatus("pending"); task.setDueAt(dueAt);
        return task;
    }

    private LeadDO lead(Long id, String status) {
        LeadDO lead = new LeadDO();
        lead.setId(id); lead.setStatus(status); lead.setSubmittedName("Lead " + id);
        return lead;
    }
}
