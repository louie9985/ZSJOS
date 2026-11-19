package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reads of the order-driven class key. The predicate mirrors the generated {@code auto_class_guard}
 * column so a lookup sees exactly the row that the unique key {@code uk_delivery_auto_class} protects.
 */
@Mapper
public interface AutoDeliveryClassMapper {
    String KEY_WHERE = " WHERE tenant_id=#{tenantId} AND auto_class_guard=#{productId} AND exam_month=#{examMonth}"
            + " AND homeroom_user_id=#{homeroomUserId} AND deleted=b'0'";

    @Select("SELECT * FROM zsjos_delivery_class" + KEY_WHERE)
    DeliveryClassDO selectByKey(@Param("tenantId") Long tenantId, @Param("productId") Long productId,
                                @Param("examMonth") String examMonth, @Param("homeroomUserId") Long homeroomUserId);

    /** Current read: after a duplicate-key race it also sees the winner committed by another transaction. */
    @Select("SELECT * FROM zsjos_delivery_class" + KEY_WHERE + " FOR UPDATE")
    DeliveryClassDO selectByKeyForUpdate(@Param("tenantId") Long tenantId, @Param("productId") Long productId,
                                         @Param("examMonth") String examMonth, @Param("homeroomUserId") Long homeroomUserId);
}
