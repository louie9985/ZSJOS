package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.service.task.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Component
public class ExamCollectionTaskSceneProvider implements BusinessTaskSceneProvider {
    public String getBizType() { return BIZ_TYPE; }
    public Map<Long, BusinessTaskDisplay> getDisplayMap(List<BusinessTaskDO> tasks) {
        var result = new HashMap<Long, BusinessTaskDisplay>();
        for (var task : tasks) if (TASK_TYPE.equals(task.getTaskType()))
            result.put(task.getId(), new BusinessTaskDisplay(task.getTitleSnapshot(), task.getSummarySnapshot(), TASK_ACTION));
        return result;
    }
}
