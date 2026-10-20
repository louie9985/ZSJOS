package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import java.util.List;

@Mapper
public interface DeliveryPerformanceReportMapper {
    @SelectProvider(type = DeliveryPerformanceReportSql.class, method = "amounts")
    List<DeliveryPerformanceAggregate> amounts(DeliveryPerformanceReportQuery query);
    @SelectProvider(type = DeliveryPerformanceReportSql.class, method = "receipts")
    List<DeliveryPerformanceFact> receipts(DeliveryPerformanceReportQuery query);
    @SelectProvider(type = DeliveryPerformanceReportSql.class, method = "cohortOrders")
    List<DeliveryPerformanceFact> cohortOrders(DeliveryPerformanceReportQuery query);
    @SelectProvider(type = DeliveryPerformanceReportSql.class, method = "calendarTasks")
    List<DeliveryPerformanceFact> calendarTasks(DeliveryPerformanceReportQuery query);
    @SelectProvider(type = DeliveryPerformanceReportSql.class, method = "pending")
    List<DeliveryPerformanceAggregate> pending(DeliveryPerformanceReportQuery query);
    @SelectProvider(type = DeliveryCurrentFollowUpSql.class, method = "pending")
    List<DeliveryPerformanceAggregate> currentFollowUps(DeliveryPerformanceReportQuery query);
    @SelectProvider(type = DeliveryPerformanceReportSql.class, method = "activity")
    List<DeliveryPerformanceAggregate> activity(DeliveryPerformanceReportQuery query);
    @SelectProvider(type = DeliveryPerformanceReportSql.class, method = "missingAttribution")
    DeliveryPerformanceAggregate missingAttribution(DeliveryPerformanceReportQuery query);
}
