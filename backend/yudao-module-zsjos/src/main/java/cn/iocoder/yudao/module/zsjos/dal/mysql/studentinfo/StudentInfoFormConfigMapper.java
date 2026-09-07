package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormConfigDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StudentInfoFormConfigMapper extends BaseMapperX<StudentInfoFormConfigDO> {
    default StudentInfoFormConfigDO state(String status) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormConfigDO>()
                .eq(StudentInfoFormConfigDO::getStatus, status)
                .orderByDesc(StudentInfoFormConfigDO::getVersionNo).last("LIMIT 1"));
    }
    default StudentInfoFormConfigDO latest() {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormConfigDO>()
                .orderByDesc(StudentInfoFormConfigDO::getVersionNo).last("LIMIT 1"));
    }
    // Serialize configuration mutations even before a tenant has its first draft.
    @Insert("INSERT IGNORE INTO zsjos_student_info_config_lock (tenant_id) VALUES (#{tenantId})")
    void ensureLock(@Param("tenantId") Long tenantId);
    @Select("SELECT tenant_id FROM zsjos_student_info_config_lock WHERE tenant_id=#{tenantId} FOR UPDATE")
    Long lockTenant(@Param("tenantId") Long tenantId);
}
