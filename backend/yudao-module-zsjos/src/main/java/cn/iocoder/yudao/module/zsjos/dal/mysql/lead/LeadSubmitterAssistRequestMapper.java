package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadSubmitterAssistRequestDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadSubmitterAssistRequestMapper extends BaseMapperX<LeadSubmitterAssistRequestDO> {

    default LeadSubmitterAssistRequestDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(LeadSubmitterAssistRequestDO::getIdempotencyKey, idempotencyKey);
    }
}
