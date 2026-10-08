package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import java.util.List;

@Mapper
public interface PerformanceReportMapper {
    @SelectProvider(type = PerformanceReportSql.class, method = "amounts")
    List<PerformanceAggregate> amounts(PerformanceReportQuery query);
    @SelectProvider(type = PerformanceReportSql.class, method = "receipts")
    List<PerformanceFact> receipts(PerformanceReportQuery query);
    @SelectProvider(type = PerformanceReportSql.class, method = "cohortOrders")
    List<PerformanceFact> cohortOrders(PerformanceReportQuery query);
    @SelectProvider(type = PerformanceReportSql.class, method = "calendarTasks")
    List<PerformanceFact> calendarTasks(PerformanceReportQuery query);
    @SelectProvider(type = PerformanceReportSql.class, method = "pending")
    List<PerformanceAggregate> pending(PerformanceReportQuery query);
    @SelectProvider(type = PerformanceReportSql.class, method = "activity")
    List<PerformanceAggregate> activity(PerformanceReportQuery query);
    @SelectProvider(type = PerformanceReportSql.class, method = "missingAttribution")
    PerformanceAggregate missingAttribution(PerformanceReportQuery query);
}
