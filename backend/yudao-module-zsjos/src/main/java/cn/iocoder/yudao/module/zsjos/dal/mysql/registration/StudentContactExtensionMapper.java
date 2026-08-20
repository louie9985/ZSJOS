package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface StudentContactExtensionMapper extends BaseMapperX<StudentContactExtensionDO> {
    default List<StudentContactExtensionDO> selectVisible(Long userId) {
        return selectList(new LambdaQueryWrapperX<StudentContactExtensionDO>()
                .and(query -> query.eq(StudentContactExtensionDO::getApplicantUserId, userId)
                        .or().eq(StudentContactExtensionDO::getReviewerUserId, userId))
                .orderByDesc(StudentContactExtensionDO::getSubmittedAt));
    }
    default StudentContactExtensionDO selectPendingByTaskId(Long taskId) {
        return selectOne(new LambdaQueryWrapperX<StudentContactExtensionDO>()
                .eq(StudentContactExtensionDO::getTaskId, taskId)
                .eq(StudentContactExtensionDO::getStatus, "pending").last("LIMIT 1"));
    }
    default StudentContactExtensionDO selectByIdempotencyKey(String key) {
        return selectOne(StudentContactExtensionDO::getIdempotencyKey, key);
    }
    @Select("SELECT * FROM zsjos_student_contact_extension WHERE process_instance_id=#{processId} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    StudentContactExtensionDO selectByProcessIdForUpdate(@Param("processId") String processId,
                                                          @Param("tenantId") Long tenantId);
}
