package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance;

import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Enrichment IDs originate exclusively from the authorized conversion population, never from request filters. */
@Mapper
public interface DeliveryConversionSnapshotMapper {
    @Data class Lead {
        private Long id;
        private String leadNo;
        private String categoryLabel;
        private String channelLabel;
    }
    @Data class Product {
        private Long orderId;
        private String ref;
        private String label;
        private String snapshot;
    }

    @Select("""
        <script>
        SELECT id, lead_no, lead_category_label_snapshot category_label,
               source_channel_label_snapshot channel_label
        FROM zsjos_lead WHERE tenant_id = #{tenant} AND deleted = 0
        AND id IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
        </script>
        """)
    List<Lead> leads(@Param("tenant") Long tenant, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT i.order_id, i.product_ref ref, i.product_snapshot snapshot,
               JSON_UNQUOTE(JSON_EXTRACT(i.product_snapshot, '$.name')) label
        FROM zsjos_order_item i JOIN zsjos_order o ON o.id = i.order_id
        AND o.tenant_id = #{tenant} AND o.deleted = 0
        WHERE i.tenant_id = #{tenant} AND i.deleted = 0
        AND i.order_id IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
        ORDER BY i.order_id, i.id
        </script>
        """)
    List<Product> products(@Param("tenant") Long tenant, @Param("ids") List<Long> ids);
}
