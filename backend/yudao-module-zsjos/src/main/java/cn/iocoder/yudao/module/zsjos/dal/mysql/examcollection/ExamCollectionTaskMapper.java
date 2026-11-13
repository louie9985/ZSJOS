package cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamCollectionTaskMapper extends BaseMapperX<ExamCollectionTaskDO> {
    default ExamCollectionTaskDO byCreateRequest(String key, boolean lock) {
        var query = new LambdaQueryWrapperX<ExamCollectionTaskDO>().eq(ExamCollectionTaskDO::getCreateRequestKey, key);
        if (lock) query.last("FOR UPDATE");
        return selectOne(query);
    }
    default ExamCollectionTaskDO byToken(String hash) {
        return selectOne(ExamCollectionTaskDO::getTokenHash, hash);
    }
    default ExamCollectionTaskDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamCollectionTaskDO>().eq(ExamCollectionTaskDO::getId, id).last("FOR UPDATE"));
    }
}
