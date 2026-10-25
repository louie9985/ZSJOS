package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassGroupDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeliveryClassGroupMapper extends BaseMapperX<DeliveryClassGroupDO> {
    @Select("SELECT * FROM zsjos_delivery_class_group WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    DeliveryClassGroupDO lock(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
