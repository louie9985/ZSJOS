package cn.iocoder.yudao.module.zsjos.service.zsjkqt;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.vo.ZsjkqtVO.Save;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.zsjkqt.ZsjkqtDraftDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.zsjkqt.ZsjkqtDraftMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZsjkqtReviewRegressionTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    private ObjectNode snapshot() {
        return (ObjectNode) JsonUtils.getObjectMapper().readTree("""
            {"schemaVersion":1,"project":{"year":2026,"month":2,"employees":[
              {"displayName":"合成人员","displayDepartment":"合成部门","enabled":true,"days":[],"summary":{"remark":"保留原文"}}
            ]}}
            """);
    }

    private ZsjkqtDraftDO winner(JsonNode snapshot) {
        var row = new ZsjkqtDraftDO();
        row.setId(7L); row.setClientId("same-request"); row.setName("原稿");
        row.setYear(2026); row.setMonth(2); row.setEmployeeCount(1); row.setConfirmedCount(0); row.setVersion(0);
        row.setSnapshotJson(JsonUtils.toJsonString(snapshot));
        return row;
    }

    @Test void concurrent_identical_create_replays_the_current_locked_winner() {
        TenantContextHolder.setTenantId(1L);
        var mapper = mock(ZsjkqtDraftMapper.class); var service = new ZsjkqtService();
        ReflectionTestUtils.setField(service, "drafts", mapper);
        var snapshot = snapshot();
        when(mapper.insert(any(ZsjkqtDraftDO.class))).thenThrow(new DuplicateKeyException("duplicate uk_zsjkqt_client"));
        when(mapper.selectByClientIdForUpdate("same-request", 1L)).thenReturn(winner(snapshot));
        assertEquals(7L, service.create(new Save("same-request", "原稿", 0, snapshot)).id());
        verify(mapper).selectByClientIdForUpdate("same-request", 1L);
    }

    @Test void conflicting_or_deleted_winner_stays_a_conflict_and_other_constraints_are_not_swallowed() {
        TenantContextHolder.setTenantId(1L);
        var mapper = mock(ZsjkqtDraftMapper.class); var service = new ZsjkqtService();
        ReflectionTestUtils.setField(service, "drafts", mapper);
        var snapshot = snapshot(); var request = new Save("same-request", "原稿", 0, snapshot);
        when(mapper.insert(any(ZsjkqtDraftDO.class))).thenThrow(new DuplicateKeyException("duplicate uk_zsjkqt_client"));
        var changed = winner(snapshot); changed.setName("其他内容");
        when(mapper.selectByClientIdForUpdate("same-request", 1L)).thenReturn(changed, null);
        assertEquals(ZsjkqtErrors.CONFLICT.getCode(), assertThrows(ServiceException.class, () -> service.create(request)).getCode());
        assertEquals(ZsjkqtErrors.CONFLICT.getCode(), assertThrows(ServiceException.class, () -> service.create(request)).getCode());
        var unrelated = new DuplicateKeyException("duplicate PRIMARY");
        when(mapper.insert(any(ZsjkqtDraftDO.class))).thenThrow(unrelated);
        assertSame(unrelated, assertThrows(DuplicateKeyException.class, () -> service.create(request)));
    }

    @Test void invalid_month_metadata_falls_back_without_altering_imported_summary() throws Exception {
        var project = (ObjectNode) snapshot().path("project");
        project.putArray("restDays").add(31); project.putArray("paidRestDays");
        project.put("shouldAttendanceDays", 18);
        ((ObjectNode) project.path("employees").get(0).path("summary")).put("shouldAttendanceDays", 21);
        var engine = new ZsjkqtExcelEngine();
        var parsed = JsonUtils.getObjectMapper().valueToTree(engine.parseReport(engine.exportReport(project), "历史正式表.xlsx"));
        var recovered = parsed.path("recoveredParameters");
        assertEquals("inferred", recovered.path("source").asText());
        assertEquals(21, recovered.path("shouldAttendanceDays").asInt());
        assertTrue(recovered.path("restDays").isEmpty());
        assertTrue(recovered.path("warnings").get(0).asText().contains("本月不存在"));
        assertEquals("保留原文", parsed.path("employees").get(0).path("summary").path("remark").asText());
    }

    @Test void single_cell_boundary_is_checked_before_save_and_legacy_export_never_truncates() throws Exception {
        var snapshot = snapshot();
        var summary = (ObjectNode) snapshot.path("project").path("employees").get(0).path("summary");
        summary.put("remark", "文".repeat(32767));
        assertDoesNotThrow(() -> ZsjkqtService.validateSnapshot(snapshot));
        var engine = new ZsjkqtExcelEngine();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(engine.exportReport(snapshot.path("project"))))) {
            assertEquals(32767, book.getSheetAt(0).getRow(1).getCell(51).getStringCellValue().length());
        }
        summary.put("remark", "文".repeat(32768));
        assertTrue(assertThrows(ServiceException.class, () -> ZsjkqtService.validateSnapshot(snapshot)).getMessage().contains("32767"));
        assertTrue(assertThrows(ServiceException.class, () -> engine.exportReport(snapshot.path("project"))).getMessage().contains("32767"));
        assertEquals(32768, summary.path("remark").asText().length());
    }

    @Test void existing_leave_classifier_outputs_can_be_saved_and_exported_then_reimported() throws Exception {
        for (String status : List.of("陪产假", "产检假")) {
            var snapshot = snapshot(); var project = (ObjectNode) snapshot.path("project");
            var day = ((ObjectNode) project.path("employees").get(0)).putArray("days").addObject().put("day", 1);
            for (String key : List.of("systemMorning", "systemAfternoon", "finalMorning", "finalAfternoon")) day.put(key, status);
            assertDoesNotThrow(() -> ZsjkqtService.validateSnapshot(snapshot));
            var engine = new ZsjkqtExcelEngine();
            var parsed = JsonUtils.getObjectMapper().valueToTree(engine.parseReport(engine.exportReport(project), "正式表.xlsx"));
            assertEquals(status, parsed.path("employees").get(0).path("days").get(0).path("morning").asText());
        }
    }
}
