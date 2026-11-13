package cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionMemberDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamCollectionMemberMapper extends BaseMapperX<ExamCollectionMemberDO> {
    default ExamCollectionMemberDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getId, id).last("FOR UPDATE"));
    }
}
