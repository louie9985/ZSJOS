package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PerformanceDetailMapper {
    @SelectProvider(type = PerformanceDetailSql.class, method = "count")
    long count(PerformanceDetailQuery query);
    @SelectProvider(type = PerformanceDetailSql.class, method = "page")
    List<PerformanceFact> page(PerformanceDetailQuery query);
    @SelectProvider(type = PerformanceDetailSql.class, method = "conversionOrders")
    List<PerformanceFact> conversionOrders(PerformanceDetailQuery query);
    @SelectProvider(type = PerformanceDetailSql.class, method = "conversionReceipts")
    List<PerformanceFact> conversionReceipts(PerformanceDetailQuery query);
    @SelectProvider(type = PerformanceDetailSql.class, method = "firstDate")
    LocalDateTime firstDate(PerformanceDetailQuery query);
}
