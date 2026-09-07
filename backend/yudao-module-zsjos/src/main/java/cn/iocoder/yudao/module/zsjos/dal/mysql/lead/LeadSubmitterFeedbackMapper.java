package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadSubmitterFeedbackDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadSubmitterFeedbackMapper extends BaseMapperX<LeadSubmitterFeedbackDO> {
    default LeadSubmitterFeedbackDO findReplay(Long leadId, Long userId, String key) {
        return selectOne(new LambdaQueryWrapperX<LeadSubmitterFeedbackDO>()
                .eq(LeadSubmitterFeedbackDO::getLeadId, leadId)
                .eq(LeadSubmitterFeedbackDO::getSalesUserId, userId)
                .eq(LeadSubmitterFeedbackDO::getIdempotencyKey, key));
    }
    default PageResult<LeadSubmitterFeedbackDO> page(Long leadId, PageParam page, String subject, Long recipientId) {
        var query = new LambdaQueryWrapperX<LeadSubmitterFeedbackDO>().eq(LeadSubmitterFeedbackDO::getLeadId, leadId);
        if ("ADMIN".equals(subject)) query.eq(LeadSubmitterFeedbackDO::getSubmitterUserId, recipientId);
        if ("PARTNER".equals(subject)) query.eq(LeadSubmitterFeedbackDO::getPartnerAccountId, recipientId);
        return selectPage(page, query.orderByDesc(LeadSubmitterFeedbackDO::getId));
    }
}

