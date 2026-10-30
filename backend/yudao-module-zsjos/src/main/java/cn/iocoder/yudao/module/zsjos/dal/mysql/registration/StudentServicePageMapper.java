package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface StudentServicePageMapper {
    @SelectProvider(type = StudentServicePageSql.class, method = "countPeople")
    @Options(timeout = 5)
    long countPeople(@Param("query") AdvancedFilterQuery query);
    @SelectProvider(type = StudentServicePageSql.class, method = "matchingOrders")
    List<cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO> matchingOrders(@Param("query") AdvancedFilterQuery query);
    @SelectProvider(type = StudentServicePageSql.class, method = "count")
    long count(@Param("query") AdvancedFilterQuery query);
    @SelectProvider(type = StudentServicePageSql.class, method = "page")
    List<ServiceRelationDO> page(@Param("query") AdvancedFilterQuery query);
}
