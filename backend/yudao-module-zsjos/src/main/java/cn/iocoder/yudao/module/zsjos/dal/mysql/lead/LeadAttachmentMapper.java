package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Collection;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

@Mapper
public interface LeadAttachmentMapper extends BaseMapperX<LeadAttachmentDO> {
    default List<LeadAttachmentDO> selectListByLeadId(Long leadId) {
        return selectList(LeadAttachmentDO::getLeadId, leadId);
    }

    default List<LeadAttachmentDO> selectListByLeadIds(Collection<Long> leadIds) {
        return selectList(new LambdaQueryWrapperX<LeadAttachmentDO>()
                .in(LeadAttachmentDO::getLeadId, leadIds)
                .orderByAsc(LeadAttachmentDO::getSort));
    }
}
