package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkAttachmentMapper extends BaseMapperX<WorkAttachmentDO> {
    default List<WorkAttachmentDO> selectListBySubject(String subjectType, Long subjectId) {
        return selectList(new LambdaQueryWrapperX<WorkAttachmentDO>().eq(WorkAttachmentDO::getSubjectType, subjectType)
                .eq(WorkAttachmentDO::getSubjectId, subjectId).orderByAsc(WorkAttachmentDO::getSort));
    }
}
