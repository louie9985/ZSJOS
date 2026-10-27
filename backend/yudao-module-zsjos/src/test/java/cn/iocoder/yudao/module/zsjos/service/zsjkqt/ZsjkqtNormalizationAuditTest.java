package cn.iocoder.yudao.module.zsjos.service.zsjkqt;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZsjkqtNormalizationAuditTest {
    private ObjectNode snapshot(String before) {
        var snapshot = (ObjectNode) JsonUtils.getObjectMapper().readTree("""
            {"schemaVersion":1,"project":{"year":2026,"month":6,"employees":[
              {"displayName":"合成审查人员","displayDepartment":"合成部门","enabled":true,"confirmed":false,
               "days":[{"day":1,"finalMorning":"出勤","finalAfternoon":"出勤","modified":true,
                        "morningNote":"人工核实","afternoonNote":"","issues":[]}],
               "summary":{"actualAttendanceDays":18.75,"personalLeaveHours":2,"remark":"保留人工汇总"}}
            ]}}
            """);
        var day = (ObjectNode) snapshot.path("project").path("employees").get(0).path("days").get(0);
        day.put("systemMorning", before).put("systemAfternoon", before);
        return snapshot;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "出勤", "休息", "带薪", "迟到", "早退", "缺卡", "异常", "加班", "旷工", "事假", "病假", "年假", "调休", "婚假", "产假", "陪产假", "产检假", "丧假", "工伤"})
    void corrected_status_and_manual_summary_survive_validation_and_real_excel_round_trip(String before) throws Exception {
        var snapshot = snapshot(before);
        assertDoesNotThrow(() -> ZsjkqtService.validateSnapshot(snapshot));
        var engine = new ZsjkqtExcelEngine();
        var parsed = JsonUtils.getObjectMapper().valueToTree(engine.parseReport(engine.exportReport(snapshot.path("project")), "合成正式表.xlsx"));
        var employee = parsed.path("employees").get(0);
        assertEquals("出勤", employee.path("days").get(0).path("morning").asText());
        assertEquals("出勤", employee.path("days").get(0).path("afternoon").asText());
        assertEquals(18.75, Double.parseDouble(employee.path("summary").path("actualAttendanceDays").asText()));
        assertEquals(2, Double.parseDouble(employee.path("summary").path("personalLeaveHours").asText()));
        assertEquals("保留人工汇总", employee.path("summary").path("remark").asText());
    }

    // Characterizes the existing narrow export contract, not approval of a complete correction audit trail.
    @ParameterizedTest
    @ValueSource(strings = {"缺卡", "迟到", "早退", "旷工", "事假", "异常"})
    void correction_list_only_retains_literal_abnormal_status_changes(String before) throws Exception {
        var engine = new ZsjkqtExcelEngine();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(engine.exportIssues(snapshot(before).path("project"))))) {
            assertEquals("异常".equals(before) ? 2 : 1, book.getSheetAt(0).getPhysicalNumberOfRows());
            if ("异常".equals(before)) {
                assertTrue(book.getSheetAt(0).getRow(1).getCell(8).getStringCellValue().contains("出勤"));
                assertEquals("人工核实", book.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
            }
        }
    }
}
