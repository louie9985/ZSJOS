package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper public interface PerformanceFactMapper {
 @Select(PerformanceFactSql.ORDERS)
 List<PerformanceFact> orders(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(PerformanceFactSql.RECEIPTS)
 List<PerformanceFact> receipts(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 // UNION ALL prevents MySQL from rescanning tenant attributions for every task.
 // Keep both fact types and LEFT JOIN semantics: missing or dual snapshots must not change historical counts.
 @Select(PerformanceFactSql.TASKS)
 List<PerformanceFact> tasks(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(PerformanceFactSql.PRODUCTS)
 List<PerformanceFact> products(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);

 @Select(PerformanceFactSql.ASSIGNMENTS)
 List<PerformanceFact> assignments(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(PerformanceFactSql.FOLLOW_UPS)
 List<PerformanceFact> followUps(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
 @Select(PerformanceFactSql.AVAILABLE_SINCE)
 java.time.LocalDateTime availableSince(@Param("tenant") Long tenant,@Param("type") String type,@Param("id") Long id);
}
