package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanSummaryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkPlanSummaryMapper extends BaseMapperX<WorkPlanSummaryDO> {
    default WorkPlanSummaryDO selectByPlanId(Long planId) {
        return selectOne(new LambdaQueryWrapperX<WorkPlanSummaryDO>().eq(WorkPlanSummaryDO::getPlanId, planId));
    }
}
