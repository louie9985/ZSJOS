package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import java.util.List;

@Mapper
public interface StudentContactExtensionMapper extends BaseMapperX<StudentContactExtensionDO> {
    default List<StudentContactExtensionDO> selectVisible(Long userId) {
        return selectList(new LambdaQueryWrapperX<StudentContactExtensionDO>()
                .and(query -> query.eq(StudentContactExtensionDO::getApplicantUserId, userId)
                        .or().eq(StudentContactExtensionDO::getReviewerUserId, userId))
                .orderByDesc(StudentContactExtensionDO::getSubmittedAt)
                .orderByDesc(StudentContactExtensionDO::getId));
    }
    default PageResult<StudentContactExtensionDO> selectVisiblePage(PageParam page, Long userId, String statusScope) {
        LambdaQueryWrapperX<StudentContactExtensionDO> query = new LambdaQueryWrapperX<>();
        query.and(scope -> scope.eq(StudentContactExtensionDO::getApplicantUserId, userId)
                .or().eq(StudentContactExtensionDO::getReviewerUserId, userId));
        if ("pending".equals(statusScope)) query.eq(StudentContactExtensionDO::getStatus, "pending");
        if ("history".equals(statusScope)) query.ne(StudentContactExtensionDO::getStatus, "pending");
        return selectPage(page, query
                .orderByDesc(StudentContactExtensionDO::getSubmittedAt)
                .orderByDesc(StudentContactExtensionDO::getId));
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

    @Select("SELECT * FROM zsjos_student_contact_extension WHERE id=#{id} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    StudentContactExtensionDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default int transitionPending(Long id, Integer version, String status, String reason,
                                  String withdrawalIdempotencyKey, LocalDateTime resolvedAt) {
        return update(null, new LambdaUpdateWrapper<StudentContactExtensionDO>()
                .eq(StudentContactExtensionDO::getId, id)
                .eq(StudentContactExtensionDO::getStatus, "pending")
                .eq(StudentContactExtensionDO::getVersion, version)
                .set(StudentContactExtensionDO::getStatus, status)
                .set(StudentContactExtensionDO::getDecisionReason, reason)
                .set(withdrawalIdempotencyKey != null, StudentContactExtensionDO::getWithdrawalIdempotencyKey,
                        withdrawalIdempotencyKey)
                .set(StudentContactExtensionDO::getResolvedAt, resolvedAt)
                .set(StudentContactExtensionDO::getVersion, version + 1));
    }
}
