package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTemplateFieldDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface WorkPlanTemplateFieldMapper extends BaseMapperX<WorkPlanTemplateFieldDO> {
    default List<WorkPlanTemplateFieldDO> selectListByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapperX<WorkPlanTemplateFieldDO>().eq(WorkPlanTemplateFieldDO::getTemplateVersionId, versionId)
                .orderByAsc(WorkPlanTemplateFieldDO::getSort).orderByAsc(WorkPlanTemplateFieldDO::getId));
    }
    @Delete("DELETE FROM zsjos_work_plan_template_field WHERE template_version_id = #{versionId}")
    void deleteHardByVersionId(Long versionId);
}
