package cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PointsRequestMapper extends BaseMapperX<PointsRequestDO> {
    @Select("SELECT * FROM zsjos_points_request WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    PointsRequestDO lock(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
