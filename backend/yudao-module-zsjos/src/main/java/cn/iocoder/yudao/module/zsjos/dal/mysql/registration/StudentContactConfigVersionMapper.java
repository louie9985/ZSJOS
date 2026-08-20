package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentContactConfigVersionMapper extends BaseMapperX<StudentContactConfigVersionDO> {
    default StudentContactConfigVersionDO selectPublished() {
        return selectOne(new LambdaQueryWrapperX<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getStatus, "published")
                .orderByDesc(StudentContactConfigVersionDO::getVersionNo).last("LIMIT 1"));
    }
    default StudentContactConfigVersionDO selectDraft() {
        return selectOne(new LambdaQueryWrapperX<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getStatus, "draft").last("LIMIT 1"));
    }
}
