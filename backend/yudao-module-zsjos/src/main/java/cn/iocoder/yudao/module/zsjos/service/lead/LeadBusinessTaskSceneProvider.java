package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskDisplay;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskSceneProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Component
public class LeadBusinessTaskSceneProvider implements BusinessTaskSceneProvider {

    @Resource
    private LeadMapper leadMapper;

    @Override
    public String getBizType() {
        return BIZ_TYPE_LEAD;
    }

    @Override
    public Map<Long, BusinessTaskDisplay> getDisplayMap(List<BusinessTaskDO> tasks) {
        Map<Long, LeadDO> leads = leadMapper.selectBatchIds(tasks.stream().map(BusinessTaskDO::getBizId)
                .distinct().toList()).stream().collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        Map<Long, BusinessTaskDisplay> result = new HashMap<>();
        for (BusinessTaskDO task : tasks) {
            LeadDO lead = leads.get(task.getBizId());
            String name = displayName(lead);
            String title = switch (task.getTaskType()) {
                case TASK_TYPE_ASSIGNMENT_ACCEPT -> "待接客资：" + name;
                case TASK_TYPE_FIRST_FOLLOW_UP -> "首次跟进：" + name;
                case TASK_TYPE_FOLLOW_UP_REMINDER -> "跟进提醒：" + name;
                case TASK_TYPE_QUALIFICATION -> "有效性判定：" + name;
                case TASK_TYPE_SUBMITTER_ASSIST -> "提交人协助：" + name;
                default -> name;
            };
            String actionCode = switch (task.getTaskType()) {
                case TASK_TYPE_ASSIGNMENT_ACCEPT -> "OPEN_LEAD_ASSIGNMENT";
                case TASK_TYPE_FIRST_FOLLOW_UP, TASK_TYPE_FOLLOW_UP_REMINDER, TASK_TYPE_QUALIFICATION ->
                        "OPEN_LEAD_FOLLOW_UP";
                case TASK_TYPE_SUBMITTER_ASSIST -> "OPEN_LEAD_SUBMITTER_ASSIST";
                default -> null;
            };
            result.put(task.getId(), new BusinessTaskDisplay(title,
                    lead == null ? null : lead.getSubmittedMobile(), actionCode));
        }
        return result;
    }

    private String displayName(LeadDO lead) {
        if (lead == null || lead.getLeadNo() == null || lead.getLeadNo().isBlank()) {
            return "客资记录不可用";
        }
        return lead.getSubmittedName() == null || lead.getSubmittedName().isBlank()
                ? lead.getLeadNo() : lead.getLeadNo() + " · " + lead.getSubmittedName();
    }
}
