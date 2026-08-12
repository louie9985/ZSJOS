package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkChangeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkChangeMapper extends BaseMapperX<WorkChangeDO> {
    default List<WorkChangeDO> selectListByPlan(Long planId, List<Long> taskIds) {
        LambdaQueryWrapperX<WorkChangeDO> query = new LambdaQueryWrapperX<>();
        query.and(q -> q.and(plan -> plan.eq(WorkChangeDO::getSubjectType, "plan").eq(WorkChangeDO::getSubjectId, planId))
                .or(taskIds != null && !taskIds.isEmpty(), task -> task.eq(WorkChangeDO::getSubjectType, "task").in(WorkChangeDO::getSubjectId, taskIds)));
        return selectList(query.orderByDesc(WorkChangeDO::getChangedAt).orderByDesc(WorkChangeDO::getId));
    }
}
