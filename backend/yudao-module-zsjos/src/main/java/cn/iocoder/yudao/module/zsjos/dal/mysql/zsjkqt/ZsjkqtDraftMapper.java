package cn.iocoder.yudao.module.zsjos.dal.mysql.zsjkqt;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.zsjkqt.ZsjkqtDraftDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface ZsjkqtDraftMapper extends BaseMapperX<ZsjkqtDraftDO> {
    @Select("SELECT * FROM zsjkqt_draft WHERE tenant_id=#{tenantId} AND client_id=#{clientId} AND deleted=FALSE FOR UPDATE")
    ZsjkqtDraftDO selectByClientIdForUpdate(@Param("clientId") String clientId, @Param("tenantId") Long tenantId);
}
