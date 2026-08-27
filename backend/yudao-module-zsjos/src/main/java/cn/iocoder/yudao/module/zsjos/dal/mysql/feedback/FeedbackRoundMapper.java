package cn.iocoder.yudao.module.zsjos.dal.mysql.feedback;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FeedbackRoundMapper extends BaseMapperX<FeedbackRoundDO> {

    default FeedbackRoundDO selectByProcessInstanceId(String id) {
        return selectOne(FeedbackRoundDO::getProcessInstanceId, id);
    }

    default List<FeedbackRoundDO> selectByFeedbackId(Long id) {
        return selectList(new LambdaQueryWrapperX<FeedbackRoundDO>()
                .eq(FeedbackRoundDO::getFeedbackId, id)
                .orderByAsc(FeedbackRoundDO::getRoundNo));
    }
}
