package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTemplateItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface WorkPlanTemplateItemMapper extends BaseMapperX<WorkPlanTemplateItemDO> {
    default List<WorkPlanTemplateItemDO> selectListByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapperX<WorkPlanTemplateItemDO>().eq(WorkPlanTemplateItemDO::getTemplateVersionId, versionId)
                .orderByAsc(WorkPlanTemplateItemDO::getSort).orderByAsc(WorkPlanTemplateItemDO::getId));
    }
    @Delete("DELETE FROM zsjos_work_plan_template_task WHERE template_version_id = #{versionId}")
    void deleteHardByVersionId(Long versionId);
}
