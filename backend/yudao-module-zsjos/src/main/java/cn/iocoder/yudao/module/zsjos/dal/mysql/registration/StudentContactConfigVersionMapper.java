package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface StudentContactConfigVersionMapper extends BaseMapperX<StudentContactConfigVersionDO> {
    default StudentContactConfigVersionDO selectPublished() {
        return selectOne(new LambdaQueryWrapperX<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getStatus, "published")
                .orderByDesc(StudentContactConfigVersionDO::getVersionNo).last("LIMIT 1"));
    }
    default StudentContactConfigVersionDO selectDraft() {
        return selectOne(new LambdaQueryWrapperX<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getStatus, "draft")
                .orderByDesc(StudentContactConfigVersionDO::getVersionNo).last("LIMIT 1"));
    }

    @Select("SELECT * FROM zsjos_student_contact_config_version WHERE id=#{id} AND tenant_id=#{tenantId} "
            + "AND status='published' AND deleted=b'0' FOR UPDATE")
    StudentContactConfigVersionDO selectPublishedByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default int updateDraft(StudentContactConfigVersionDO value, Integer expectedVersion) {
        return update(null, new LambdaUpdateWrapper<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getId, value.getId())
                .eq(StudentContactConfigVersionDO::getStatus, "draft")
                .eq(StudentContactConfigVersionDO::getVersion, expectedVersion)
                .set(StudentContactConfigVersionDO::getFirstContactTimeoutMinutes, value.getFirstContactTimeoutMinutes())
                .set(StudentContactConfigVersionDO::getStudyPlanTimeoutMinutes, value.getStudyPlanTimeoutMinutes())
                .set(StudentContactConfigVersionDO::getChecklistJson, value.getChecklistJson())
                .set(StudentContactConfigVersionDO::getQuickNotesJson, value.getQuickNotesJson())
                .set(StudentContactConfigVersionDO::getCollaboratorTabsJson, value.getCollaboratorTabsJson())
                .set(StudentContactConfigVersionDO::getVersion, expectedVersion + 1));
    }

    default int publishDraft(Long id, Integer expectedVersion) {
        return update(null, new LambdaUpdateWrapper<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getId, id)
                .eq(StudentContactConfigVersionDO::getStatus, "draft")
                .eq(StudentContactConfigVersionDO::getVersion, expectedVersion)
                .set(StudentContactConfigVersionDO::getStatus, "published")
                .set(StudentContactConfigVersionDO::getVersion, expectedVersion + 1));
    }
}
