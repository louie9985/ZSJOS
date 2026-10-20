package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeliveryPerformanceDetailMapper {
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "historicalPeople")
    List<DeliveryPerformanceFact> historicalPeople(DeliveryPerformanceDetailQuery query);
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "unclassifiedCount")
    long unclassifiedCount(DeliveryPerformanceDetailQuery query);
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "unclassifiedPage")
    List<DeliveryPerformanceFact> unclassifiedPage(DeliveryPerformanceDetailQuery query);
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "count")
    long count(DeliveryPerformanceDetailQuery query);
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "page")
    List<DeliveryPerformanceFact> page(DeliveryPerformanceDetailQuery query);
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "conversionOrders")
    List<DeliveryPerformanceFact> conversionOrders(DeliveryPerformanceDetailQuery query);
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "conversionReceipts")
    List<DeliveryPerformanceFact> conversionReceipts(DeliveryPerformanceDetailQuery query);
    @SelectProvider(type = DeliveryPerformanceDetailSql.class, method = "firstDate")
    LocalDateTime firstDate(DeliveryPerformanceDetailQuery query);
}
