package cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.StudentServiceArchiveRevisionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentServiceArchiveRevisionMapper extends BaseMapperX<StudentServiceArchiveRevisionDO> {
    default StudentServiceArchiveRevisionDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<StudentServiceArchiveRevisionDO>().eq(StudentServiceArchiveRevisionDO::getId, id).last("FOR UPDATE"));
    }
}
