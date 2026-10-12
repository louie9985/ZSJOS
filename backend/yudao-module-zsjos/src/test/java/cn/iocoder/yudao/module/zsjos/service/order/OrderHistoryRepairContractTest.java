package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual readers against the private migration evidence; never emits its payload. */
@EnabledIfEnvironmentVariable(named = "ZSJOS_ORDER_REPAIR_PLAN", matches = ".+")
class OrderHistoryRepairContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void generatedSnapshotsRoundTripThroughProductionReaders() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
            Map<String, Object> plan = JsonUtils.getObjectMapper().readValue(
                    Files.readString(Path.of(System.getenv("ZSJOS_ORDER_REPAIR_PLAN"))), Map.class);
            List<Map<String, Object>> operations = (List<Map<String, Object>>) plan.get("operations");
            int rounds = 0;
            int products = 0;
            for (Map<String, Object> operation : operations) {
                Map<String, Object> after = (Map<String, Object>) operation.get("after");
                if ("zsjos_order_approval_round".equals(operation.get("table"))) {
                    String raw = JsonUtils.toJsonString(after.get("order_snapshot"));
                    SalesOrderSnapshot snapshot = SalesOrderSnapshot.read(raw);
                    assertEquals(2, snapshot.getSnapshotVersion(), "Generated history must be readable V2");
                    assertNotNull(snapshot.getOrder());
                    assertNotNull(snapshot.getCapturedAt());
                    SalesOrderDO current = new SalesOrderDO();
                    current.setStatus("effective");
                    SalesOrderDO projected = snapshot.project(current);
                    assertEquals("effective", projected.getStatus());
                    assertEquals(snapshot.getOrder().getTotalAmount(), projected.getTotalAmount());
                    SalesOrderRespVO response = new SalesOrderRespVO();
                    snapshot.apply(response);
                    assertFalse(response.getHistoryMissingFields().containsKey("snapshot"));
                    SalesOrderSnapshot reread = SalesOrderSnapshot.read(JsonUtils.toJsonString(snapshot));
                    assertEquals(snapshot.getCapturedAt(), reread.getCapturedAt());
                    assertEquals(snapshot.getOrder().getCustomerPaidAt(), reread.getOrder().getCustomerPaidAt());
                    assertEquals(snapshot.getOrderLabels(), reread.getOrderLabels());
                    SalesOrderGiftSnapshot.read(projected.getGiftItems());
                    rounds++;
                }
                if ("zsjos_order_item".equals(operation.get("table"))
                        && ((Number) after.get("deleted")).intValue() == 0) {
                    LeadProductSnapshot product = LeadProductSnapshot.readHistorical(
                            JsonUtils.toJsonString(after.get("product_snapshot")));
                    assertNotNull(product);
                    assertNotNull(product.name());
                    assertNotNull(product.categoryPath());
                    assertTrue(product.skuUnknown());
                    assertNull(product.skuName(), "Absent source SKU must not be invented");
                    products++;
                }
            }
            assertEquals(320, rounds);
            assertEquals(36, products);
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
