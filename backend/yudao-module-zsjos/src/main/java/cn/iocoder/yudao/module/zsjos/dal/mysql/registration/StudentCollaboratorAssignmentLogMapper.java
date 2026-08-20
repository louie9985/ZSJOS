package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentCollaboratorAssignmentLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentCollaboratorAssignmentLogMapper extends BaseMapperX<StudentCollaboratorAssignmentLogDO> {
    default StudentCollaboratorAssignmentLogDO selectByIdempotencyKey(String key) {
        return selectOne(StudentCollaboratorAssignmentLogDO::getIdempotencyKey, key);
    }
}
