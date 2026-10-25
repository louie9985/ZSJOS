package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.MenuTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.LeadTaskReminderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
public class MenuTaskSummaryServiceImpl implements MenuTaskSummaryService {
    @Resource private BusinessTaskMapper taskMapper;
    private final Clock clock;

    public MenuTaskSummaryServiceImpl() {
        this(Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    MenuTaskSummaryServiceImpl(Clock clock) {
        this.clock = clock.withZone(ZoneId.of("Asia/Shanghai"));
    }

    @Override
    public MenuTaskSummaryRespVO getMySummary(Long userId) {
        var instant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(instant, clock.getZone());
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        List<BusinessTaskDO> tasks = taskMapper.selectMyPending(userId);
        Map<String, List<BusinessTaskDO>> groups = tasks.stream()
                .filter(task -> isReminderEligible(task, tomorrow))
                .filter(task -> !"media_account_diagnosis".equals(task.getBizType())
                        || task.getDueAt() != null && !task.getDueAt().isAfter(now.plusDays(7)))
                .map(task -> new AbstractMap.SimpleEntry<>(menuPath(task), task))
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        List<MenuTaskSummaryRespVO.Item> items = groups.entrySet().stream().map(entry -> {
            List<BusinessTaskDO> group = entry.getValue();
            BusinessTaskDO first = group.get(0);
            boolean urgent = group.stream().anyMatch(task -> task.getDueAt() != null && task.getDueAt().isBefore(now));
            String query = targetQuery(first);
            int count=(int)group.stream().map(task -> "media_account_diagnosis".equals(task.getBizType())
                    ? "diagnosis:"+task.getBizId()+":"+task.getDueAt()+":"+cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSource.parse(task.getPayload()).get("roundKey")
                    : "task:"+task.getId()).distinct().count();
            return new MenuTaskSummaryRespVO.Item(entry.getKey(), count, urgent ? "urgent" : "normal",
                    group.stream().map(BusinessTaskDO::getTaskType).filter(Objects::nonNull).distinct().toList(),
                    new MenuTaskSummaryRespVO.Target(entry.getKey(), query));
        }).toList();
        return new MenuTaskSummaryRespVO(instant.toEpochMilli(), items.stream().mapToLong(MenuTaskSummaryRespVO.Item::getCount).sum(), items);
    }

    @Override
    public Map<Long, List<LeadTaskReminderRespVO>> getMyLeadReminders(Long userId, Collection<Long> visibleLeadIds) {
        if (userId == null || visibleLeadIds.isEmpty()) return Map.of();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        Set<Long> visible = new HashSet<>(visibleLeadIds);
        // Reuse effective task responsibility and menu timing, then intersect with already-authorized Leads.
        // Never infer a personal obligation from visibility of a subordinate's Lead or its lifecycle status.
        return eligibleLeadTasks(userId, tomorrow).stream()
                .filter(task -> visible.contains(task.getBizId()))
                .collect(Collectors.groupingBy(BusinessTaskDO::getBizId, LinkedHashMap::new,
                        Collectors.mapping(task -> new LeadTaskReminderRespVO(task.getId(), task.getTaskType(),
                                reminderTitle(task), task.getDueAt(), task.getDueAt() != null && task.getDueAt().isBefore(now)),
                                Collectors.toList())));
    }

    @Override
    public List<Long> getMyLeadReminderIds(Long userId) {
        if (userId == null) return List.of();
        LocalDateTime tomorrow = LocalDateTime.ofInstant(clock.instant(), clock.getZone()).toLocalDate().plusDays(1).atStartOfDay();
        return eligibleLeadTasks(userId, tomorrow).stream().map(BusinessTaskDO::getBizId).distinct().toList();
    }

    private List<BusinessTaskDO> eligibleLeadTasks(Long userId, LocalDateTime tomorrow) {
        return taskMapper.selectMyPending(userId).stream()
                .filter(task -> BIZ_TYPE_LEAD.equals(task.getBizType()) && task.getBizId() != null)
                .filter(task -> "/zsjos/leads/manage".equals(menuPath(task)) && isReminderEligible(task, tomorrow)).toList();
    }

    private String reminderTitle(BusinessTaskDO task) {
        if (task.getTitleSnapshot() != null && !task.getTitleSnapshot().isBlank()) return task.getTitleSnapshot();
        return switch (task.getTaskType()) {
            case TASK_TYPE_FIRST_FOLLOW_UP -> "首次跟进";
            case TASK_TYPE_FOLLOW_UP_REMINDER -> "跟进提醒";
            case TASK_TYPE_QUALIFICATION -> "客资判定";
            case TASK_TYPE_SUBMITTER_ASSIST -> "提交人协助";
            case TASK_TYPE_ASSIGNMENT_ACCEPT -> "待接单";
            default -> "待处理任务";
        };
    }

    private List<BusinessTaskDO> studentTasks(Long userId, LocalDateTime tomorrow) {
        if (userId == null) return List.of();
        return taskMapper.selectMyPending(userId).stream()
                .filter(task -> "student_service".equals(task.getBizType()) && task.getBizId() != null
                        && isReminderEligible(task, tomorrow)).toList();
    }

    @Override
    public List<Long> getMyStudentReminderIds(Long userId) {
        LocalDateTime tomorrow = LocalDateTime.ofInstant(clock.instant(), clock.getZone()).toLocalDate().plusDays(1).atStartOfDay();
        return studentTasks(userId, tomorrow).stream().map(BusinessTaskDO::getBizId).distinct().toList();
    }

    @Override
    public Map<Long, List<cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.StudentTaskReminderRespVO>>
            getMyStudentReminders(Long userId, Collection<Long> visibleRelationIds) {
        if (visibleRelationIds.isEmpty()) return Map.of();
        var visible = new HashSet<>(visibleRelationIds);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        // Keep row indicators and priority aligned with menu timing, not another user's summary/currentTask.
        return studentTasks(userId, tomorrow).stream().filter(task -> visible.contains(task.getBizId()))
                .collect(Collectors.groupingBy(BusinessTaskDO::getBizId, LinkedHashMap::new,
                        Collectors.mapping(task -> new cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.StudentTaskReminderRespVO(
                                task.getId(), task.getTaskType(), studentTaskTitle(task), task.getDueAt(),
                                task.getDueAt() != null && task.getDueAt().isBefore(now)), Collectors.toList())));
    }

    private String studentTaskTitle(BusinessTaskDO task) {
        if (task.getTitleSnapshot() != null && !task.getTitleSnapshot().isBlank()) return task.getTitleSnapshot();
        return switch (task.getTaskType()) {
            case "student_first_contact" -> "学员首联";
            case "student_study_plan" -> "制定学习计划";
            case "student_contact" -> "学员跟进";
            case "student_first_contact_assistance" -> "首联协助";
            case "student_delivery_stage" -> "交付阶段任务";
            default -> "学员待处理任务";
        };
    }

    private boolean isReminderEligible(BusinessTaskDO task, LocalDateTime tomorrow) {
        if (!TASK_STATUS_PENDING.equals(task.getStatus())) return false;
        // Scheduled student tasks wait for their due day; undated immediate obligations remain actionable.
        if ("student_service".equals(task.getBizType())) {
            return task.getDueAt() == null || task.getDueAt().isBefore(tomorrow);
        }
        // Future tasks remain in task lists; Lead reminders retain their existing timing rules.
        if (BIZ_TYPE_LEAD.equals(task.getBizType())
                && (TASK_TYPE_FOLLOW_UP_REMINDER.equals(task.getTaskType())
                || TASK_TYPE_QUALIFICATION.equals(task.getTaskType()))) {
            return task.getDueAt() != null && task.getDueAt().isBefore(tomorrow);
        }
        return true;
    }

    private String menuPath(BusinessTaskDO task) {
        if ("sales_order".equals(task.getBizType())) return "/zsjos/my-sales-orders";
        if ("student_service".equals(task.getBizType())) return "/zsjos/my-students";
        if ("birthday_care".equals(task.getBizType())) return "/zsjos/tasks/today";
        if ("lead".equals(task.getBizType())) {
            return switch (task.getTaskType()) {
                case "assignment_accept" -> "/zsjos/leads/assignment";
                default -> "/zsjos/leads/manage";
            };
        }
        if (task.getActionCode() != null && task.getActionCode().startsWith("OPEN_WORK_TASK")) return "/zsjos/work-plans";
        return "/zsjos/tasks/today";
    }

    private String targetQuery(BusinessTaskDO task) {
        if (task.getBizId() == null) return null;
        if ("sales_order".equals(task.getBizType())) return "orderId=" + task.getBizId();
        if ("student_service".equals(task.getBizType())) return "serviceRelationId=" + task.getBizId();
        if ("lead".equals(task.getBizType())) return "leadId=" + task.getBizId();
        return null;
    }
}
