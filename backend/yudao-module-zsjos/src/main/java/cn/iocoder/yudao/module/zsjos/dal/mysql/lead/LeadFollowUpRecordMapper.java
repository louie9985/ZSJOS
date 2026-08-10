package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LeadFollowUpRecordMapper extends BaseMapperX<LeadFollowUpRecordDO> {
    default LeadFollowUpRecordDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                .eq(LeadFollowUpRecordDO::getIdempotencyKey, key));
    }

    default PageResult<LeadFollowUpRecordDO> selectPageByLeadId(Long leadId, long pageNo, long pageSize) {
        cn.iocoder.yudao.framework.common.pojo.PageParam pageParam =
                new cn.iocoder.yudao.framework.common.pojo.PageParam();
        pageParam.setPageNo(Math.toIntExact(pageNo));
        pageParam.setPageSize(Math.toIntExact(pageSize));
        return selectPage(pageParam,
                new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                        .eq(LeadFollowUpRecordDO::getLeadId, leadId)
                        .orderByDesc(LeadFollowUpRecordDO::getOccurredAt)
                        .orderByDesc(LeadFollowUpRecordDO::getId));
    }

    default List<LeadFollowUpRecordDO> selectListByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                .eq(LeadFollowUpRecordDO::getLeadId, leadId)
                .orderByDesc(LeadFollowUpRecordDO::getOccurredAt)
                .orderByDesc(LeadFollowUpRecordDO::getId));
    }
}
