package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderDetailMapper {
    @Data class Aggregate { private long orders; private BigDecimal amount; }
    @Data class Facet { private String dimension; private String key; private String label; private long count; }
    @Data class Row {
        private Long id; private String number; private Long leadId; private String leadNo;
        private String sourceLabel; private String orderTypeLabel; private String categoryLabel; private String channelLabel;
        private LocalDateTime submittedAt; private BigDecimal amount; private BigDecimal totalAmount;
    }
    @Data class Product { private Long orderId; private String key; private String ref; private String label; }

    @SelectProvider(type = OrderDetailSql.class, method = "summary") Aggregate summary(OrderDetailQuery query);
    @SelectProvider(type = OrderDetailSql.class, method = "filteredSummary") Aggregate filteredSummary(OrderDetailQuery query);
    @SelectProvider(type = OrderDetailSql.class, method = "options") List<Facet> options(OrderDetailQuery query);
    @SelectProvider(type = OrderDetailSql.class, method = "page") List<Row> page(OrderDetailQuery query);
    @SelectProvider(type = OrderDetailSql.class, method = "pageProducts") List<Product> pageProducts(OrderDetailQuery query);
}
