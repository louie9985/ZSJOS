package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadPublicSeaRecordMapper extends BaseMapperX<LeadPublicSeaRecordDO> {
    default LeadPublicSeaRecordDO selectByLeadId(Long leadId) {
        return selectOne(LeadPublicSeaRecordDO::getLeadId, leadId);
    }
}
