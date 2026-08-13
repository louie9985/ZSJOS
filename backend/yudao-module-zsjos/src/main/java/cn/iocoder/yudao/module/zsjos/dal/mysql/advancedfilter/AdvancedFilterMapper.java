package cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter;

import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdvancedFilterMapper {
    @SelectProvider(type = SqlProvider.class, method = "leadSql")
    List<Long> selectLeadIds(@Param("query") AdvancedFilterQuery query);

    @SelectProvider(type = SqlProvider.class, method = "orderSql")
    List<Long> selectOrderIds(@Param("query") AdvancedFilterQuery query);

    final class SqlProvider {
        public static String leadSql(Map<String, Object> ignored) {
            return "SELECT l.id FROM zsjos_lead l WHERE l.deleted=b'0' AND l.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
        }
        public static String orderSql(Map<String, Object> ignored) {
            return "SELECT o.id FROM zsjos_order o WHERE o.deleted=b'0' AND o.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
        }
    }
}
