package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadSubmitterFeedbackAttachmentDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LeadSubmitterFeedbackAttachmentMapper extends BaseMapperX<LeadSubmitterFeedbackAttachmentDO> {
    default LeadSubmitterFeedbackAttachmentDO findFile(Long fileId) {
        return selectOne(new LambdaQueryWrapperX<LeadSubmitterFeedbackAttachmentDO>()
                .eq(LeadSubmitterFeedbackAttachmentDO::getFileId, fileId));
    }
    default List<LeadSubmitterFeedbackAttachmentDO> listByFeedback(Long feedbackId) {
        return selectList(new LambdaQueryWrapperX<LeadSubmitterFeedbackAttachmentDO>()
                .eq(LeadSubmitterFeedbackAttachmentDO::getFeedbackId, feedbackId)
                .orderByAsc(LeadSubmitterFeedbackAttachmentDO::getSort));
    }
}

