package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.MenuTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MenuTaskSummaryServiceImpl implements MenuTaskSummaryService {
    @Resource private BusinessTaskMapper taskMapper;

    @Override
    public MenuTaskSummaryRespVO getMySummary(Long userId) {
        List<BusinessTaskDO> tasks = taskMapper.selectMyPending(userId);
        Map<String, List<BusinessTaskDO>> groups = tasks.stream()
                .map(task -> new AbstractMap.SimpleEntry<>(menuPath(task), task))
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        List<MenuTaskSummaryRespVO.Item> items = groups.entrySet().stream().map(entry -> {
            List<BusinessTaskDO> group = entry.getValue();
            BusinessTaskDO first = group.get(0);
            boolean urgent = group.stream().anyMatch(task -> task.getDueAt() != null && task.getDueAt().isBefore(java.time.LocalDateTime.now()));
            String query = targetQuery(first);
            return new MenuTaskSummaryRespVO.Item(entry.getKey(), group.size(), urgent ? "urgent" : "normal",
                    group.stream().map(BusinessTaskDO::getTaskType).filter(Objects::nonNull).distinct().toList(),
                    new MenuTaskSummaryRespVO.Target(entry.getKey(), query));
        }).toList();
        return new MenuTaskSummaryRespVO(Instant.now().toEpochMilli(), items.stream().mapToLong(MenuTaskSummaryRespVO.Item::getCount).sum(), items);
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
