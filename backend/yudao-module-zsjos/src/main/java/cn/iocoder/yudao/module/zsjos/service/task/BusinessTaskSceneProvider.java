package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;

import java.util.List;
import java.util.Map;

public interface BusinessTaskSceneProvider {

    String getBizType();

    Map<Long, BusinessTaskDisplay> getDisplayMap(List<BusinessTaskDO> tasks);

}
