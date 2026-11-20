package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TextbookShippingMapper {
    record Shipment(Long itemId, String carrier, String trackingNo, int version) {}

    @Select("SELECT id AS itemId, textbook_carrier AS carrier, textbook_tracking_no AS trackingNo, textbook_shipping_version AS version FROM zsjos_order_item WHERE tenant_id=#{tenant} AND order_id=#{orderId} AND deleted=0 AND textbook_shipping_version>0")
    List<Shipment> forOrder(@Param("orderId") Long orderId, @Param("tenant") Long tenant);

    @Update("UPDATE zsjos_order_item SET textbook_carrier=#{carrier}, textbook_tracking_no=#{trackingNo}, textbook_shipping_version=textbook_shipping_version+1, updater=#{actor}, update_time=NOW() WHERE tenant_id=#{tenant} AND order_id=#{orderId} AND id=#{itemId} AND deleted=0 AND textbook_shipping_version=#{version}")
    int save(@Param("tenant") Long tenant, @Param("orderId") Long orderId, @Param("itemId") Long itemId,
             @Param("version") int version, @Param("carrier") String carrier, @Param("trackingNo") String trackingNo,
             @Param("actor") Long actor);
}
