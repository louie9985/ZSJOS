package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface LeadAssignmentRelationMapper extends BaseMapperX<LeadAssignmentRelationDO> {

    default List<LeadAssignmentRelationDO> selectListBySourceUserIds(String scene,
                                                                     Collection<Long> sourceUserIds) {
        return selectList(new LambdaQueryWrapperX<LeadAssignmentRelationDO>()
                .eq(LeadAssignmentRelationDO::getScene, scene)
                .in(LeadAssignmentRelationDO::getSourceUserId, sourceUserIds));
    }

    default Long selectCountByScene(String scene) {
        return selectCount(LeadAssignmentRelationDO::getScene, scene);
    }

}
