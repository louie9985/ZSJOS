package cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionBindingDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamCollectionBindingMapper extends BaseMapperX<ExamCollectionBindingDO> {
    default ExamCollectionBindingDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamCollectionBindingDO>().eq(ExamCollectionBindingDO::getId, id).last("FOR UPDATE"));
    }
}
