package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkPlanTypeMapper extends BaseMapperX<WorkPlanTypeDO> {
    default List<WorkPlanTypeDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<WorkPlanTypeDO>().eq(WorkPlanTypeDO::getStatus, 0)
                .orderByAsc(WorkPlanTypeDO::getSort).orderByAsc(WorkPlanTypeDO::getId));
    }
}
