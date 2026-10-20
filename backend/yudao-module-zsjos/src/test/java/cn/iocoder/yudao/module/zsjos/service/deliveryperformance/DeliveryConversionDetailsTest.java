package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryConversionDetailVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryConversionDetailsTest {
    final LocalDateTime now = LocalDateTime.of(2026,10,10,12,0);
    DeliveryPerformanceFact receipt(long id, LocalDateTime time) {
        var row=new DeliveryPerformanceFact();row.setLeadId(id);row.setId(id);row.setNumber("KZ-"+id);
        row.setUserId(1L);row.setOwnerUserId(1L);row.setCurrentAssignment(true);row.setReceivedAt(time);row.setStatus("valid");return row;
    }
    DeliveryPerformanceFact order(long id, long lead, LocalDateTime received, LocalDateTime submitted, String amount) {
        var row=receipt(lead,received);row.setId(id);row.setNumber("DD-"+id);row.setOccurredAt(submitted);
        row.setOrderType("first_purchase");row.setAmount(new BigDecimal(amount));return row;
    }
    DeliveryConversionSnapshotMapper.Lead lead(long id,String category,String channel) {
        var row=new DeliveryConversionSnapshotMapper.Lead();row.setId(id);row.setLeadNo("KZ-"+id);row.setCategoryLabel(category);row.setChannelLabel(channel);return row;
    }
    DeliveryConversionSnapshotMapper.Product product(long id,String ref,String name) {
        var row=new DeliveryConversionSnapshotMapper.Product();row.setOrderId(id);row.setRef(ref);row.setLabel(name);row.setSnapshot("{\"name\":\""+name+"\"}");return row;
    }
    @ParameterizedTest @ValueSource(strings={"month","lastMonth","last7","last30","last60","last90"})
    void filtersAreAppliedBeforePagingWithoutChangingPopulationOrRate(String key) {
        var window=DeliveryPerformancePeriods.window(key,now);var received=window.start().plusHours(1);var submitted=window.end().minusMinutes(1);
        var prior=window.start().minusDays(1);
        // Monthly fixtures must remain inside the exact 60-day boundary; rolling permits long observations.
        var receipts=List.of(receipt(1,received),receipt(2,received),receipt(3,prior),receipt(4,received));
        var orders=List.of(order(10,1,received,submitted,"1280"),order(11,1,received,submitted.plusSeconds(1),"2000"),
                order(12,2,received,submitted,"1279.99"),order(13,3,prior,window.start().plusDays(1),"1280"));
        var population=DeliveryPerformanceConversion.population(window,orders,receipts);
        var leads=List.of(lead(1,"分类甲","渠道甲"),lead(2,"分类乙","渠道甲"),lead(3,"分类甲","渠道乙"),lead(4,null,null));
        var products=List.of(product(10,"ref|1","商品甲"),product(10,"ref|1","商品甲"),product(10,"ref2","商品乙"),product(11,"later","后续订单商品"),product(13,"ref|1","商品甲"));
        var q=new Query();q.setPageSize(1);
        var result=DeliveryConversionDetails.assemble(q,window,now,population,leads,products);
        assertEquals(population.size(),result.summary().denominator());assertEquals(population.size(),result.total());
        assertEquals(DeliveryPerformanceStatisticsService.metric(window,orders,receipts).rate(),result.summary().rate());
        assertTrue(result.options().products().stream().noneMatch(p->p.label().equals("后续订单商品")));
        var ids=new HashSet<Long>();for(int page=1;page<=result.total();page++){q.setPageNo(page);assertTrue(ids.add(DeliveryConversionDetails.assemble(q,window,now,population,leads,products).list().getFirst().leadId()));}
        assertEquals(population.keySet(),ids);q.setPageNo(1);
        q.setConversionStatus("converted");q.setPopulationKind("current");q.setCategoryKey(DeliveryConversionDetails.snapshotKey("分类甲"));q.setChannelKey(DeliveryConversionDetails.snapshotKey("渠道甲"));
        q.setProductKey(result.options().products().stream().filter(p->p.label().equals("商品乙")).findFirst().orElseThrow().key());q.setKeyword(" dd-10 ");
        var filtered=DeliveryConversionDetails.assemble(q,window,now,population,leads,products);
        assertEquals(1,filtered.total());assertEquals(1L,filtered.list().getFirst().leadId());assertEquals(2,filtered.list().getFirst().order().products().size());
        assertEquals(result.summary(),filtered.summary());assertEquals(result.options(),filtered.options());
        q.setPageNo(2);assertTrue(DeliveryConversionDetails.assemble(q,window,now,population,leads,products).list().isEmpty());q.setPageNo(1);
        q.setConversionStatus("unconverted");assertEquals(0,DeliveryConversionDetails.assemble(q,window,now,population,leads,products).total());
        q=new Query();q.setCategoryKey("missing");var missing=DeliveryConversionDetails.assemble(q,window,now,population,leads,products);
        assertEquals(4L,missing.list().getFirst().leadId());assertNull(missing.list().getFirst().order());
        q=new Query();q.setPopulationKind("previous");assertEquals(DeliveryPerformanceConversion.rolling(key)?0:1,DeliveryConversionDetails.assemble(q,window,now,population,leads,products).total());
    }
    @Test void missingMetadataAndEmptyPopulationRemainExplicit() {
        var window=DeliveryPerformancePeriods.window("month",now);var order=order(10,1,now.minusDays(15),now.minusDays(1),"1280");
        var q=new Query();var result=DeliveryConversionDetails.assemble(q,window,now,DeliveryPerformanceConversion.population(window,List.of(order),List.of()),List.of(),List.of());
        assertNull(result.list().getFirst().leadNo());assertEquals("missing",result.list().getFirst().channelKey());assertTrue(result.list().getFirst().order().products().isEmpty());
        var empty=DeliveryConversionDetails.assemble(q,window,now,Map.of(),List.of(),List.of());assertEquals(0,empty.total());assertNull(empty.summary().rate());
        assertNotEquals(DeliveryConversionDetails.snapshotKey(null),DeliveryConversionDetails.snapshotKey("missing"));
        assertNotEquals(DeliveryConversionDetails.snapshotKey("甲|乙"),DeliveryConversionDetails.snapshotKey("甲"));
    }
}
