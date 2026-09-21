package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SalesOrderSnapshotTest {
    @Test void transactionFactsRemainHistoricalWhileLifecycleStaysCurrent() {
        SalesOrderSnapshot value = new SalesOrderSnapshot();
        value.setSnapshotVersion(2);
        SalesOrderSnapshot.Facts facts = new SalesOrderSnapshot.Facts();
        facts.setStudentName("录单时姓名"); facts.setPaymentMethod("bank");
        value.setOrder(facts);
        value.setOrderLabels(Map.of("paymentMethod", "当时银行转账"));
        value.setSubmitter(new SalesOrderSnapshot.Actor("ADMIN", 12L, "当时提交人", "sales_conversion"));
        SalesOrderDO current = new SalesOrderDO();
        current.setStudentName("后来姓名"); current.setStatus("effective"); current.setId(7L);
        SalesOrderSnapshot read = SalesOrderSnapshot.read(JsonUtils.toJsonString(value));
        SalesOrderDO projected = read.project(current);
        assertEquals("录单时姓名", projected.getStudentName());
        assertEquals("effective", projected.getStatus()); assertEquals(7L, projected.getId());
        assertEquals("后来姓名", current.getStudentName());
        SalesOrderRespVO response = new SalesOrderRespVO(); read.apply(response);
        assertEquals("当时银行转账", response.getPaymentMethodLabelSnapshot());
        assertEquals("当时提交人", response.getSubmitterUserName());
        assertFalse(response.getHistoryMissingFields().containsKey("paymentMethod"));
    }

    @Test void legacySnapshotRetainsLabelsAndDoesNotManufactureNames() {
        SalesOrderSnapshot read = SalesOrderSnapshot.read("{\"snapshotVersion\":1,\"order\":{\"id\":7,\"studentName\":\"旧姓名\"},\"items\":[],\"orderLabels\":{\"paymentMethod\":\"已删除字典标签\"}}");
        SalesOrderRespVO response = new SalesOrderRespVO(); read.apply(response);
        assertEquals("已删除字典标签", response.getPaymentMethodLabelSnapshot());
        assertNull(response.getSubmitterUserName());
        assertEquals("history_not_recorded", response.getHistoryMissingFields().get("submitterUserName"));
    }

    @Test void malformedSnapshotIsDistinctFromMissingHistory() {
        SalesOrderRespVO response = new SalesOrderRespVO();
        SalesOrderSnapshot.read("{broken").apply(response);
        assertEquals("invalid_snapshot", response.getHistoryMissingFields().get("snapshot"));
        SalesOrderSnapshot.read(null).apply(response);
        assertFalse(response.getHistoryMissingFields().containsKey("snapshot"));
    }
}
