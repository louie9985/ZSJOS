package cn.iocoder.yudao.module.zsjos.dal.mysql.feedback;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackConfigDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FeedbackConfigMapper extends BaseMapperX<FeedbackConfigDO> {

    default FeedbackConfigDO selectByType(String type) {
        return selectOne(FeedbackConfigDO::getFeedbackType, type);
    }

    default List<FeedbackConfigDO> selectAll() {
        return selectList(new LambdaQueryWrapperX<FeedbackConfigDO>()
                .orderByAsc(FeedbackConfigDO::getId));
    }
}
