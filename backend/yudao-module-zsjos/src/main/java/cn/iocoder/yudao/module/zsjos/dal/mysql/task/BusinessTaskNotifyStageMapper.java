package cn.iocoder.yudao.module.zsjos.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskNotifyStageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessTaskNotifyStageMapper extends BaseMapperX<BusinessTaskNotifyStageDO> {
    default boolean exists(Long taskId, Integer taskVersion, String stage) {
        return selectCount(new LambdaQueryWrapperX<BusinessTaskNotifyStageDO>()
                .eq(BusinessTaskNotifyStageDO::getTaskId, taskId)
                .eq(BusinessTaskNotifyStageDO::getTaskVersion, taskVersion)
                .eq(BusinessTaskNotifyStageDO::getStage, stage)) > 0;
    }
}
