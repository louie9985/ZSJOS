package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import java.util.List;
import java.util.Map;

public interface HomeMetricSources {
    record Field(String key, String label, String unit) {}
    record Source(String key, String label, String scene, List<Field> fields, List<Field> timeFields, String route) {}
    List<Source> catalog(Long actor);
    MetricEvaluation.Sources bind(HomeMetricSpec spec, Long actor);
    PageResult<Map<String,Object>> details(HomeMetricSpec.Operand operand, MetricPeriod.Range range,
                                         Long actor, int pageNo, int pageSize);
}
