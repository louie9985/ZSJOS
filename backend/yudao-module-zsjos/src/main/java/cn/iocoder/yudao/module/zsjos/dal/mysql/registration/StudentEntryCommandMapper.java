package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentEntryCommandDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StudentEntryCommandMapper extends BaseMapperX<StudentEntryCommandDO> {
    @Insert("INSERT IGNORE INTO zsjos_student_entry_command(tenant_id,operator_user_id,command_key,request_hash) "
            + "VALUES(#{tenantId},#{actor},#{key},#{hash})")
    int reserve(@Param("tenantId") Long tenantId, @Param("actor") Long actor,
                @Param("key") String key, @Param("hash") String hash);
    @Select("SELECT * FROM zsjos_student_entry_command WHERE tenant_id=#{tenantId} "
            + "AND operator_user_id=#{actor} AND command_key=#{key} AND deleted=b'0' FOR UPDATE")
    StudentEntryCommandDO lock(@Param("tenantId") Long tenantId, @Param("actor") Long actor, @Param("key") String key);
}
