package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OrderActorRepairSnapshotTest {
    private SalesOrderDO order() {
        SalesOrderDO o = new SalesOrderDO();
        o.setTenantId(1L); o.setOrderNo("ORDER-TEST"); o.setSubmitterUserId(17L);
        o.setFormalSalesUserId(30L); o.setStatus("effective");
        return o;
    }

    private String repair(Long tenant, String no, String type, Long id, String name) {
        return JsonUtils.toJsonString(new SalesOrderSnapshot.RepairedActors(1, tenant, no,
                "order-actors-v312", "2026-10-12T16:00:00+08:00",
                new SalesOrderSnapshot.Actor(type, id, name, null), null));
    }

    @Test void explicitRepairDisplaysWithoutInventingAnApprovalRound() {
        SalesOrderDO o = order(); o.setRepairedActorSnapshot(repair(1L,"ORDER-TEST","ADMIN",17L,"补录姓名"));
        SalesOrderSnapshot snapshot = SalesOrderSnapshot.read(null,o);
        SalesOrderRespVO response = new SalesOrderRespVO(); snapshot.apply(response);
        assertEquals("补录姓名",response.getSubmitterUserName());
        assertNull(o.getCurrentApprovalRoundId());
        assertEquals(17L,snapshot.project(o).getSubmitterUserId());
        assertEquals("effective",snapshot.project(o).getStatus());
    }

    @Test void crossTenantOrderActorDomainAndChangedOwnerCannotOverrideHistory() {
        String original="{\"submitter\":{\"subjectType\":\"ADMIN\",\"id\":17,\"name\":\"原姓名\"}}";
        for (String raw : List.of(repair(2L,"ORDER-TEST","ADMIN",17L,"新姓名"),
                repair(1L,"OTHER","ADMIN",17L,"新姓名"),repair(1L,"ORDER-TEST","LEGACY_EMPLOYEE",17L,"新姓名"),
                repair(1L,"ORDER-TEST","ADMIN",99L,"新姓名"),repair(1L,"ORDER-TEST","ADMIN",17L," "),"broken")) {
            SalesOrderDO o=order(); o.setRepairedActorSnapshot(raw);
            assertEquals("原姓名",SalesOrderSnapshot.read(original,o).getSubmitter().name());
        }
    }

    @Test void repairOverridesPersonnelOnlyAndDoesNotChangeBusinessFacts() {
        SalesOrderDO o=order(); o.setRepairedActorSnapshot(repair(1L,"ORDER-TEST","ADMIN",17L,"修正姓名"));
        SalesOrderSnapshot s=SalesOrderSnapshot.read("""
          {"snapshotVersion":2,"order":{"totalAmount":120},"orderLabels":{"feeMode":"原标签"},
           "submitter":{"subjectType":"LEGACY_EMPLOYEE","id":19,"name":"旧姓名"},
           "formalSales":{"subjectType":"ADMIN","id":30,"name":"销售姓名"}}
          """,o);
        assertEquals("修正姓名",s.getSubmitter().name());
        assertEquals("销售姓名",s.getFormalSales().name());
        assertEquals("原标签",s.label("feeMode"));
        assertEquals(0,s.getOrder().getTotalAmount().compareTo(new java.math.BigDecimal("120")));
        assertEquals(30L,s.project(o).getFormalSalesUserId());
    }

    @Test
    @EnabledIfEnvironmentVariable(named="ZSJOS_ACTOR_REPAIR_AFTER",matches=".+")
    @SuppressWarnings("unchecked")
    void allControlledDatabaseSnapshotsUseTheProductionReader() throws Exception {
        Map<String,Object> data=JsonUtils.getObjectMapper().readValue(
                Files.readString(Path.of(System.getenv("ZSJOS_ACTOR_REPAIR_AFTER"))),Map.class);
        List<Map<String,Object>> orders=(List<Map<String,Object>>)data.get("zsjos_order");
        List<Map<String,Object>> rounds=(List<Map<String,Object>>)data.get("zsjos_order_approval_round");
        int read=0;
        for (Map<String,Object> row:orders) {
            if (row.get("repaired_actor_snapshot")==null) continue;
            SalesOrderDO o=order(); o.setOrderNo((String)row.get("order_no"));
            o.setSubmitterUserId(((Number)row.get("submitter_user_id")).longValue());
            o.setFormalSalesUserId(row.get("formal_sales_user_id")==null?null:((Number)row.get("formal_sales_user_id")).longValue());
            o.setRepairedActorSnapshot((String)row.get("repaired_actor_snapshot"));
            String raw=rounds.stream().filter(r->java.util.Objects.equals(r.get("id"),row.get("current_approval_round_id")))
                    .map(r->(String)r.get("order_snapshot")).findFirst().orElse(null);
            SalesOrderSnapshot s=SalesOrderSnapshot.read(raw,o);
            assertNotEquals(-1,s.getSnapshotVersion());
            assertEquals(o.getSubmitterUserId(),s.getSubmitter().id());
            assertEquals("ADMIN",s.getSubmitter().subjectType());
            assertNotNull(s.getSubmitter().name());
            SalesOrderRespVO response=new SalesOrderRespVO();s.apply(response);
            assertEquals(s.getSubmitter().name(),response.getSubmitterUserName());
            read++;
        }
        assertEquals(37,read);
    }
}
