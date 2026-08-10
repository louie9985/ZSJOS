package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.BUSINESS_TASK_BUCKET_INVALID;

@Service
public class BusinessTaskServiceImpl implements BusinessTaskService {
    private static final Set<String> BUCKETS = Set.of("unscheduled", "overdue", "today", "future");
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private LeadMapper leadMapper;

    @Override
    public BusinessTaskSummaryRespVO getMySummary(Long userId) {
        List<BusinessTaskDO> tasks = taskMapper.selectMyPending(userId);
        return new BusinessTaskSummaryRespVO(count(tasks, "unscheduled"), count(tasks, "overdue"),
                count(tasks, "today"), count(tasks, "future"));
    }

    @Override
    public PageResult<BusinessTaskRespVO> getMyPage(Long userId, String bucket, int pageNo, int pageSize) {
        if (!BUCKETS.contains(bucket)) throw exception(BUSINESS_TASK_BUCKET_INVALID);
        List<BusinessTaskDO> matched = taskMapper.selectMyPending(userId).stream()
                .filter(task -> inBucket(task, bucket)).sorted(taskComparator()).toList();
        int from = Math.min((pageNo - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        List<BusinessTaskDO> page = matched.subList(from, to);
        Map<Long, LeadDO> leads = new HashMap<>();
        if (!page.isEmpty()) {
            leadMapper.selectBatchIds(page.stream().map(BusinessTaskDO::getBizId).distinct().toList())
                    .forEach(lead -> leads.put(lead.getId(), lead));
        }
        return new PageResult<>(page.stream().map(task -> convert(task, leads.get(task.getBizId()))).toList(),
                (long) matched.size());
    }

    private long count(List<BusinessTaskDO> tasks, String bucket) {
        return tasks.stream().filter(task -> inBucket(task, bucket)).count();
    }

    private boolean inBucket(BusinessTaskDO task, String bucket) {
        LocalDateTime dueAt = task.getDueAt();
        LocalDate today = LocalDate.now();
        return switch (bucket) {
            case "unscheduled" -> dueAt == null;
            case "overdue" -> dueAt != null && dueAt.isBefore(LocalDateTime.now());
            case "today" -> dueAt != null && !dueAt.isBefore(LocalDateTime.now())
                    && dueAt.toLocalDate().equals(today);
            case "future" -> dueAt != null && dueAt.toLocalDate().isAfter(today);
            default -> false;
        };
    }

    private Comparator<BusinessTaskDO> taskComparator() {
        return Comparator.comparing(BusinessTaskDO::getDueAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(BusinessTaskDO::getId);
    }

    private BusinessTaskRespVO convert(BusinessTaskDO task, LeadDO lead) {
        BusinessTaskRespVO result = new BusinessTaskRespVO();
        result.setId(task.getId()); result.setTaskType(task.getTaskType());
        result.setBizType(task.getBizType()); result.setBizId(task.getBizId());
        String name = lead == null ? "客资 #" + task.getBizId() : lead.getSubmittedName();
        result.setTitle(switch (task.getTaskType()) {
            case TASK_TYPE_ASSIGNMENT_ACCEPT -> "待接客资：" + name;
            case TASK_TYPE_FIRST_FOLLOW_UP -> "首次跟进：" + name;
            case TASK_TYPE_FOLLOW_UP_REMINDER -> "跟进提醒：" + name;
            case TASK_TYPE_QUALIFICATION -> "有效性判定：" + name;
            default -> name;
        });
        result.setSummary(lead == null ? null : lead.getSubmittedMobile());
        result.setDueAt(task.getDueAt());
        result.setOverdue(task.getDueAt() != null && task.getDueAt().isBefore(LocalDateTime.now()));
        result.setActionCode(TASK_TYPE_ASSIGNMENT_ACCEPT.equals(task.getTaskType())
                ? "OPEN_LEAD_ASSIGNMENT" : "OPEN_LEAD_FOLLOW_UP");
        return result;
    }
}
