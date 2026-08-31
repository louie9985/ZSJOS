package cn.iocoder.yudao.module.zsjos.service.birthdaycare;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskDisplay;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskSceneProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BirthdayCareBusinessTaskSceneProvider implements BusinessTaskSceneProvider {
    @Override
    public String getBizType() {
        return BirthdayCareConstants.BIZ_TYPE;
    }

    @Override
    public Map<Long, BusinessTaskDisplay> getDisplayMap(List<BusinessTaskDO> tasks) {
        Map<Long, BusinessTaskDisplay> result = new HashMap<>();
        for (BusinessTaskDO task : tasks) {
            result.put(task.getId(), new BusinessTaskDisplay(task.getTitleSnapshot(),
                    task.getSummarySnapshot(), BirthdayCareConstants.ACTION_COMPLETE));
        }
        return result;
    }
}
