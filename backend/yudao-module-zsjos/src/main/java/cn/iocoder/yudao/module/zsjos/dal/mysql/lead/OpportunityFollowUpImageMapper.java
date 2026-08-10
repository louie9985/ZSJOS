package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityFollowUpImageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface OpportunityFollowUpImageMapper extends BaseMapperX<OpportunityFollowUpImageDO> {
    default List<OpportunityFollowUpImageDO> selectListByRecordIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<OpportunityFollowUpImageDO>()
                .in(OpportunityFollowUpImageDO::getFollowUpRecordId, ids)
                .orderByAsc(OpportunityFollowUpImageDO::getSort));
    }
}
