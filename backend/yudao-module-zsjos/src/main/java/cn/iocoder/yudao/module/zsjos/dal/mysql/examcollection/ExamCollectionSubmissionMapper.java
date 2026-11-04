package cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionSubmissionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamCollectionSubmissionMapper extends BaseMapperX<ExamCollectionSubmissionDO> {
    default ExamCollectionSubmissionDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>().eq(ExamCollectionSubmissionDO::getId, id).last("FOR UPDATE"));
    }
}
