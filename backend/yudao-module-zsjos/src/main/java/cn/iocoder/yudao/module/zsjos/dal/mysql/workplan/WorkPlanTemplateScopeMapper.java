package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTemplateScopeDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface WorkPlanTemplateScopeMapper extends BaseMapperX<WorkPlanTemplateScopeDO> {
    default List<WorkPlanTemplateScopeDO> selectListByTemplateId(Long templateId) { return selectList(new LambdaQueryWrapperX<WorkPlanTemplateScopeDO>().eq(WorkPlanTemplateScopeDO::getTemplateId, templateId).orderByAsc(WorkPlanTemplateScopeDO::getId)); }
    @Delete("DELETE FROM zsjos_work_plan_template_scope WHERE template_id = #{templateId}")
    void deleteHardByTemplateId(Long templateId);
}
