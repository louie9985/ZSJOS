package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkPlanTemplateMapper extends BaseMapperX<WorkPlanTemplateDO> {
    default List<WorkPlanTemplateDO> selectEnabledByType(Long typeId) {
        return selectList(new LambdaQueryWrapperX<WorkPlanTemplateDO>().eq(WorkPlanTemplateDO::getTypeId, typeId)
                .eq(WorkPlanTemplateDO::getStatus, "published").orderByAsc(WorkPlanTemplateDO::getId));
    }
}
