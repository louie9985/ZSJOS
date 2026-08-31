package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTemplateVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkPlanTemplateVersionMapper extends BaseMapperX<WorkPlanTemplateVersionDO> {
    default WorkPlanTemplateVersionDO selectPublished(Long templateId) {
        return selectOne(new LambdaQueryWrapperX<WorkPlanTemplateVersionDO>().eq(WorkPlanTemplateVersionDO::getTemplateId, templateId)
                .eq(WorkPlanTemplateVersionDO::getStatus, "published").orderByDesc(WorkPlanTemplateVersionDO::getVersionNo).last("LIMIT 1"));
    }
    default List<WorkPlanTemplateVersionDO> selectListByTemplateId(Long templateId) {
        return selectList(new LambdaQueryWrapperX<WorkPlanTemplateVersionDO>().eq(WorkPlanTemplateVersionDO::getTemplateId, templateId)
                .orderByDesc(WorkPlanTemplateVersionDO::getVersionNo));
    }
}
