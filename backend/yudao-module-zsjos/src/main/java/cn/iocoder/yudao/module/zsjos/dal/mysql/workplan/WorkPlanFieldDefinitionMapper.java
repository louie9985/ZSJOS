package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanFieldDefinitionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkPlanFieldDefinitionMapper extends BaseMapperX<WorkPlanFieldDefinitionDO> {
    default List<WorkPlanFieldDefinitionDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<WorkPlanFieldDefinitionDO>().eq(WorkPlanFieldDefinitionDO::getPlanId, planId)
                .orderByAsc(WorkPlanFieldDefinitionDO::getSection).orderByAsc(WorkPlanFieldDefinitionDO::getSort).orderByAsc(WorkPlanFieldDefinitionDO::getId));
    }
    @Delete("DELETE FROM zsjos_work_plan_field_definition WHERE plan_id = #{planId}")
    void deleteHardByPlanId(Long planId);
}
