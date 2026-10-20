package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper public interface DeliveryPerformanceFactMapper {
 @Select(DeliveryPerformanceFactSql.ORDERS)
 List<DeliveryPerformanceFact> orders(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(DeliveryPerformanceFactSql.RECEIPTS)
 List<DeliveryPerformanceFact> receipts(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 // UNION ALL prevents MySQL from rescanning tenant attributions for every task.
 // Keep both fact types and LEFT JOIN semantics: missing or dual snapshots must not change historical counts.
 @Select(DeliveryPerformanceFactSql.TASKS)
 List<DeliveryPerformanceFact> tasks(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(DeliveryPerformanceFactSql.PRODUCTS)
 List<DeliveryPerformanceFact> products(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);

 @Select(DeliveryPerformanceFactSql.ASSIGNMENTS)
 List<DeliveryPerformanceFact> assignments(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(DeliveryPerformanceFactSql.FOLLOW_UPS)
 List<DeliveryPerformanceFact> followUps(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(DeliveryPerformanceFactSql.AVAILABLE_SINCE)
 java.time.LocalDateTime availableSince(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
}
