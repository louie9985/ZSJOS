package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpImageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface LeadFollowUpImageMapper extends BaseMapperX<LeadFollowUpImageDO> {
    default List<LeadFollowUpImageDO> selectListByRecordIds(Collection<Long> recordIds) {
        if (recordIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<LeadFollowUpImageDO>()
                .in(LeadFollowUpImageDO::getFollowUpRecordId, recordIds)
                .orderByAsc(LeadFollowUpImageDO::getSort));
    }
}
