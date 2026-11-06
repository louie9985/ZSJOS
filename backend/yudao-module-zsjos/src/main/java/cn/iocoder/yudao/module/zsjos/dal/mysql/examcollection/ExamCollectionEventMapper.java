package cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamCollectionEventMapper extends BaseMapperX<ExamCollectionEventDO> {
    default ExamCollectionEventDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamCollectionEventDO>().eq(ExamCollectionEventDO::getId, id).last("FOR UPDATE"));
    }
}
