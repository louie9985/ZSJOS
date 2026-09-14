package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.service.task.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Component
public class MediaAccountDiagnosisTaskSceneProvider implements BusinessTaskSceneProvider {
    private static final Set<String> TYPES = Set.of(TASK_TYPE_ACCOUNT_DIAGNOSIS_7D, TASK_TYPE_ACCOUNT_DIAGNOSIS_14D, TASK_TYPE_ACCOUNT_DIAGNOSIS_28D);
    public String getBizType() { return "media_account_diagnosis"; }
    public Map<Long, BusinessTaskDisplay> getDisplayMap(List<BusinessTaskDO> tasks) {
        Map<Long, BusinessTaskDisplay> result = new HashMap<>();
        for (BusinessTaskDO task : tasks) if (TYPES.contains(task.getTaskType()))
            result.put(task.getId(), new BusinessTaskDisplay(task.getTitleSnapshot(), task.getSummarySnapshot(), "MEDIA_ACCOUNT_DIAGNOSIS"));
        return result;
    }
}
