package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryOrderDetailVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeliveryOrderDetailsService {
    @Resource private DeliveryOrderDetailMapper mapper;

    public Response read(Query query, DeliveryPerformanceReportQuery scope, DeliveryPerformancePeriods.Window window, LocalDateTime now) {
        if (query.getDimension() != null && !Set.of("source", "product", "contributor").contains(query.getDimension()))
            throw DeliveryPerformanceAccess.invalid("订单明细不支持该下钻维度");
        if ((query.getDimension() == null) != (query.getGroupKey() == null))
            throw DeliveryPerformanceAccess.invalid("下钻维度和条件必须同时提供");
        if (query.getKeyword() != null) query.setKeyword(query.getKeyword().strip());
        var q = new DeliveryOrderDetailQuery();
        q.setBusinessDomain(scope.getBusinessDomain()).setTenant(scope.getTenant()).setType(scope.getType()).setId(scope.getId())
            .setAllDepartments(scope.isAllDepartments()).setMissingDepartment(scope.isMissingDepartment()).setDepartments(scope.getDepartments())
            .setStart(window.start()).setEnd(window.end()).setDimension(query.getDimension()).setGroupKey(query.getGroupKey())
            .setSize(query.getPageSize()).setOffset((long) (query.getPageNo() - 1) * query.getPageSize());
        q.setFilters(query);
        var summary = summary(mapper.summary(q));
        var filtered = summary(mapper.filteredSummary(q));
        var facets = mapper.options(q);
        var options = new Options(options(facets, "source"), options(facets, "order_type"), options(facets, "category"), options(facets, "channel"), options(facets, "product"));
        List<Row> rows = List.of();
        if (q.getOffset() < filtered.orders()) {
            var page = mapper.page(q);
            var products = mapper.pageProducts(q).stream().collect(Collectors.groupingBy(DeliveryOrderDetailMapper.Product::getOrderId));
            rows = page.stream().map(r -> new Row(r.getId(), r.getNumber(), r.getLeadId(), r.getLeadNo(), r.getSourceLabel(),
                r.getOrderTypeLabel(), r.getCategoryLabel(), r.getChannelLabel(), r.getSubmittedAt(), r.getAmount(), r.getTotalAmount(),
                products.getOrDefault(r.getId(), List.of()).stream().map(p -> new Product(p.getKey(), p.getRef(), p.getLabel())).toList())).toList();
        }
        return new Response(rows, filtered.orders(), now, window.start(), window.end(), "product".equals(query.getDimension()) ? "product" : "order", summary, filtered, options);
    }
    private static Summary summary(DeliveryOrderDetailMapper.Aggregate value) { return new Summary(value.getOrders(), value.getAmount()); }
    private static List<Option> options(List<DeliveryOrderDetailMapper.Facet> facets, String dimension) {
        return facets.stream().filter(f -> dimension.equals(f.getDimension())).sorted(Comparator.comparing(DeliveryOrderDetailMapper.Facet::getKey))
            .map(f -> new Option(f.getKey(), f.getLabel(), f.getCount())).toList();
    }
}
