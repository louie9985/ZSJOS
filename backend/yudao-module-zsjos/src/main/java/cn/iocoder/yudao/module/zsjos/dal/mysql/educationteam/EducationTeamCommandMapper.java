package cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.educationteam.EducationTeamCommandDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EducationTeamCommandMapper extends BaseMapperX<EducationTeamCommandDO> {
    default EducationTeamCommandDO selectByOperatorAndKey(Long operatorUserId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<EducationTeamCommandDO>()
                .eq(EducationTeamCommandDO::getOperatorUserId, operatorUserId)
                .eq(EducationTeamCommandDO::getIdempotencyKey, idempotencyKey));
    }

    @Insert("INSERT IGNORE INTO zsjos_subordinate_sales_command(operator_user_id,idempotency_key,action_type,"
            + "request_fingerprint,result_json,completed,creator,create_time,updater,update_time,deleted,tenant_id) "
            + "VALUES(#{row.operatorUserId},#{row.idempotencyKey},#{row.actionType},#{row.requestFingerprint},NULL,"
            + "b'0','',NOW(),'',NOW(),b'0',#{tenantId})")
    int insertIgnore(@Param("tenantId") Long tenantId, @Param("row") EducationTeamCommandDO row);

    @Update("UPDATE zsjos_subordinate_sales_command SET result_json=#{resultJson},completed=b'1',update_time=NOW() "
            + "WHERE tenant_id=#{tenantId} AND operator_user_id=#{operatorUserId} AND idempotency_key=#{key} "
            + "AND deleted=b'0' AND completed=b'0'")
    int complete(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId,
                 @Param("key") String key, @Param("resultJson") String resultJson);
}
