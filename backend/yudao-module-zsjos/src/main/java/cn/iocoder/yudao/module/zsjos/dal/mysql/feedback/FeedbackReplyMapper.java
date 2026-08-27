package cn.iocoder.yudao.module.zsjos.dal.mysql.feedback;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackReplyDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FeedbackReplyMapper extends BaseMapperX<FeedbackReplyDO> {

    default FeedbackReplyDO selectByFeedbackAndKey(Long id, String key) {
        return selectOne(new LambdaQueryWrapperX<FeedbackReplyDO>()
                .eq(FeedbackReplyDO::getFeedbackId, id)
                .eq(FeedbackReplyDO::getIdempotencyKey, key));
    }

    default List<FeedbackReplyDO> selectByFeedbackId(Long id) {
        return selectList(new LambdaQueryWrapperX<FeedbackReplyDO>()
                .eq(FeedbackReplyDO::getFeedbackId, id)
                .orderByAsc(FeedbackReplyDO::getCreateTime)
                .orderByAsc(FeedbackReplyDO::getId));
    }
}
