package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonContactClaimDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PersonContactClaimMapper extends BaseMapperX<PersonContactClaimDO> {
    @Insert("INSERT IGNORE INTO zsjos_person_contact_claim(contact_value,person_id,reservation_key,creator,create_time,updater,update_time,deleted,tenant_id) "
            + "VALUES(#{value},NULL,#{reservationKey},'',NOW(),'',NOW(),b'0',#{tenantId})")
    int reserve(@Param("tenantId") Long tenantId, @Param("value") String value,
                @Param("reservationKey") String reservationKey);

    @Select("SELECT * FROM zsjos_person_contact_claim WHERE tenant_id=#{tenantId} AND contact_value=#{value} "
            + "AND deleted=b'0' FOR UPDATE")
    PersonContactClaimDO selectByValueForUpdate(@Param("tenantId") Long tenantId, @Param("value") String value);

    @Update("UPDATE zsjos_person_contact_claim SET person_id=#{personId},reservation_key=NULL,updater='',update_time=NOW() "
            + "WHERE tenant_id=#{tenantId} AND reservation_key=#{reservationKey} AND deleted=b'0'")
    int bindReservations(@Param("tenantId") Long tenantId, @Param("reservationKey") String reservationKey,
                         @Param("personId") Long personId);

    @Select("SELECT * FROM zsjos_person_contact_claim WHERE tenant_id=#{tenantId} AND person_id=#{personId} "
            + "AND deleted=b'0' FOR UPDATE")
    List<PersonContactClaimDO> selectByPersonIdForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("personId") Long personId);

    @Delete("<script>DELETE FROM zsjos_person_contact_claim WHERE tenant_id=#{tenantId} AND person_id=#{personId}"
            + "<if test='values != null and !values.isEmpty()'> AND contact_value NOT IN "
            + "<foreach collection='values' item='value' open='(' separator=',' close=')'>#{value}</foreach></if></script>")
    int deleteStale(@Param("tenantId") Long tenantId, @Param("personId") Long personId,
                    @Param("values") List<String> values);
}
