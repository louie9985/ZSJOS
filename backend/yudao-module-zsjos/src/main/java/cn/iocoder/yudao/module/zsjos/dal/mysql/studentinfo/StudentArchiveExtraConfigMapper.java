package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentArchiveExtraConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentArchiveExtraConfigMapper extends BaseMapperX<StudentArchiveExtraConfigDO> {
    default StudentArchiveExtraConfigDO current() {
        return selectOne(new LambdaQueryWrapperX<StudentArchiveExtraConfigDO>()
                .eq(StudentArchiveExtraConfigDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .last("LIMIT 1"));
    }

    default StudentArchiveExtraConfigDO currentForUpdate() {
        return selectOne(new LambdaQueryWrapperX<StudentArchiveExtraConfigDO>()
                .eq(StudentArchiveExtraConfigDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .last("FOR UPDATE"));
    }
}
