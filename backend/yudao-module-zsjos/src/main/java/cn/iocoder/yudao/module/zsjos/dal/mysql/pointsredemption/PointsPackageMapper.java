package cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsPackageDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PointsPackageMapper extends BaseMapperX<PointsPackageDO> {
    @Select("SELECT * FROM zsjos_points_package WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    PointsPackageDO lock(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
