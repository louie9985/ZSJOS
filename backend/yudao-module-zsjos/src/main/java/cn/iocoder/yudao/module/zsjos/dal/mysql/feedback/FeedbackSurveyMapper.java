package cn.iocoder.yudao.module.zsjos.dal.mysql.feedback;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackSurveyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeedbackSurveyMapper extends BaseMapperX<FeedbackSurveyDO> {

    default FeedbackSurveyDO selectByFeedbackId(Long id) {
        return selectOne(FeedbackSurveyDO::getFeedbackId, id);
    }
}
