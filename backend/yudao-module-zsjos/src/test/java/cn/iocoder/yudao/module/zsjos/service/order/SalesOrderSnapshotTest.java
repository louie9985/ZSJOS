package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SalesOrderSnapshotTest {
    private SalesOrderDO importedOrder(Long evidenceTenant, String evidenceOrderNo, Long ownerId) {
        SalesOrderDO order = new SalesOrderDO();
        order.setTenantId(1L); order.setOrderNo("OD-HISTORY-1");
        order.setSubmitterUserId(20L); order.setFormalSalesUserId(30L);
        order.setStatus("effective"); order.setVersion(4);
        order.setImportedActorSnapshot(JsonUtils.toJsonString(new SalesOrderSnapshot.ImportedActors(
                1, evidenceTenant, evidenceOrderNo, "parttimecrm", "a".repeat(64), 91L,
                "2026-09-20T22:21:03+08:00",
                new SalesOrderSnapshot.ImportedActor(20L, 120L, "备份录单姓名", "legacy_backup_profile"),
                new SalesOrderSnapshot.ImportedActor(ownerId, 130L, "成交历史姓名", "legacy_order_snapshot"))));
        return order;
    }

    @Test void importedEvidenceWorksWithoutAnApprovalRoundAndDoesNotChangeOwnership() {
        SalesOrderDO order = importedOrder(1L, "OD-HISTORY-1", 30L);
        SalesOrderSnapshot history = SalesOrderSnapshot.read(null, order);
        SalesOrderRespVO response = new SalesOrderRespVO();
        history.apply(response);
        assertEquals("备份录单姓名", response.getSubmitterUserName());
        assertEquals("成交历史姓名", response.getFormalSalesUserName());
        assertFalse(response.getHistoryMissingFields().containsKey("formalSalesUserName"));
        assertEquals("LEGACY_EMPLOYEE", history.getSubmitter().subjectType());
        assertEquals(120L, history.getSubmitter().id());
        SalesOrderDO projected = history.project(order);
        assertEquals(20L, projected.getSubmitterUserId());
        assertEquals(30L, projected.getFormalSalesUserId());
        assertEquals("effective", projected.getStatus());
        assertEquals(4, projected.getVersion());
        assertNull(projected.getCurrentApprovalRoundId());
    }

    @Test void recordedRoundNamesAlwaysWinOverImportedEvidence() {
        SalesOrderSnapshot read = SalesOrderSnapshot.read("""
                {"snapshotVersion":2,"submitter":{"subjectType":"ADMIN","id":20,"name":"提交时姓名"},
                 "formalSales":{"subjectType":"ADMIN","id":30,"name":"审批轮次成交姓名"}}
                """, importedOrder(1L, "OD-HISTORY-1", 30L));
        assertEquals("提交时姓名", read.getSubmitter().name());
        assertEquals("审批轮次成交姓名", read.getFormalSales().name());
        assertEquals("ADMIN", read.getSubmitter().subjectType());
    }

    @Test void mismatchedEvidenceCannotLeakAcrossTenantsOrdersOrChangedOwners() {
        assertNull(SalesOrderSnapshot.read(null, importedOrder(2L, "OD-HISTORY-1", 30L)).getSubmitter());
        assertNull(SalesOrderSnapshot.read(null, importedOrder(1L, "OD-OTHER", 30L)).getSubmitter());
        SalesOrderSnapshot changedOwner = SalesOrderSnapshot.read(null, importedOrder(1L, "OD-HISTORY-1", 99L));
        assertNull(changedOwner.getFormalSales());
        assertNotNull(changedOwner.getSubmitter());
        SalesOrderSnapshot differentRoundActor = SalesOrderSnapshot.read(
                "{\"submitter\":{\"subjectType\":\"ADMIN\",\"id\":99}}",
                importedOrder(1L, "OD-HISTORY-1", 30L));
        assertNull(differentRoundActor.getSubmitter().name());
    }

    @Test void invalidEvidenceDoesNotHideRecordedHistoryOrInventMissingNames() {
        SalesOrderDO order = importedOrder(1L, "OD-HISTORY-1", 30L);
        order.setImportedActorSnapshot("{invalid");
        assertNull(SalesOrderSnapshot.read(null, order).getSubmitter());
        assertEquals("已保存", SalesOrderSnapshot.read(
                "{\"submitter\":{\"subjectType\":\"ADMIN\",\"id\":20,\"name\":\"已保存\"}}", order).getSubmitter().name());
        assertNull(SalesOrderSnapshot.read("{invalid", importedOrder(1L, "OD-HISTORY-1", 30L)).getSubmitter());
    }

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
