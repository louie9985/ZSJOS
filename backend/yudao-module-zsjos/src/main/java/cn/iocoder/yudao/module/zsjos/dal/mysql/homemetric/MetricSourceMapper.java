package cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric;

import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.*;

@Mapper
public interface MetricSourceMapper {
    @Select("${query.whereSql}") @Options(timeout=5)
    BigDecimal aggregate(@Param("query") AdvancedFilterQuery query);
    @Select("${query.whereSql}") @Options(timeout=5)
    List<Map<String,Object>> details(@Param("query") AdvancedFilterQuery query);
}
