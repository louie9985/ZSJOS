package cn.iocoder.yudao.module.zsjos.service.zsjkqt;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executable port of all 15 original Rust engine tests plus all four ExcelJS parser cases. */
class ZsjkqtExcelEngineTest {
    private final ZsjkqtExcelEngine engine=new ZsjkqtExcelEngine();
    static JsonNode json(String value){return JsonUtils.getObjectMapper().readTree(value);}
    static JsonNode project(){return json("""
      {"year":2026,"month":6,"restDays":[7,14],"paidRestDays":[1],"shouldAttendanceDays":21,"payrollDays":23,
       "employees":[{"displayName":"张三","displayDepartment":"教务部","enabled":true,"sourceIndex":1,
        "days":[{"day":1,"finalMorning":"出勤","finalAfternoon":"事假"}],
        "summary":{"shouldAttendanceDays":21,"actualAttendanceDays":99,"payrollDays":23,"entryDate":"2026-06-01","personalLeaveHours":7,"remark":"原表备注"}}]}
      """);}
    @Test void date_header_uses_month_day_and_weekday(){assertEquals("6月1日星期一",ZsjkqtExcelEngine.dateHeader(2026,6,1));assertEquals("6月30日星期二",ZsjkqtExcelEngine.dateHeader(2026,6,30));assertEquals("2月29日星期四",ZsjkqtExcelEngine.dateHeader(2024,2,29));}
    @Test void exported_sheet_month_requires_year(){assertArrayEquals(new int[]{2026,6},ZsjkqtExcelEngine.sheetMonth("2026年6月考勤"));assertThrows(IllegalArgumentException.class,()->ZsjkqtExcelEngine.sheetMonth("6月考勤"));}
    @Test void exported_attendance_report_can_be_reimported() throws Exception {
        JsonNode data=project();var object=(tools.jackson.databind.node.ObjectNode)data.path("employees").get(0);var days=(tools.jackson.databind.node.ArrayNode)object.path("days");for(int d=2;d<=30;d++)days.addObject().put("day",d).put("finalMorning","").put("finalAfternoon","");
        byte[] bytes=engine.exportReport(data);JsonNode r=JsonUtils.getObjectMapper().valueToTree(engine.parseReport(bytes,"续改.xlsx"));assertEquals(2026,r.path("year").asInt());assertEquals("张三",r.path("employees").get(0).path("name").asText());assertEquals("教务部",r.path("employees").get(0).path("department").asText());assertEquals("出勤",r.path("employees").get(0).path("days").get(0).path("morning").asText());assertEquals("事假",r.path("employees").get(0).path("days").get(0).path("afternoon").asText());assertEquals(99,r.path("employees").get(0).path("summary").path("actualAttendanceDays").asInt());assertEquals("原表备注",r.path("employees").get(0).path("summary").path("remark").asText());assertEquals("metadata",r.path("recoveredParameters").path("source").asText());assertEquals(json("[7,14]"),r.path("recoveredParameters").path("restDays"));
        try(var b=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertEquals(SheetVisibility.VERY_HIDDEN,b.getSheetVisibility(1));assertEquals(38,b.getSheetAt(0).getRow(1).getHeightInPoints());assertEquals(18*256,b.getSheetAt(0).getColumnWidth(4));}
    }
    @Test void summary_columns_follow_actual_month_length(){assertEquals(35,ZsjkqtExcelEngine.summaryStart(2026,1));assertEquals(34,ZsjkqtExcelEngine.summaryStart(2026,6));assertEquals(33,ZsjkqtExcelEngine.summaryStart(2024,2));assertEquals(32,ZsjkqtExcelEngine.summaryStart(2026,2));}
    @Test void status_display_uses_text_for_plain_statuses(){assertEquals("出勤",ZsjkqtExcelEngine.display("√"));assertEquals("休息",ZsjkqtExcelEngine.display("△"));assertEquals("带薪",ZsjkqtExcelEngine.display("带薪假"));assertEquals("迟到",ZsjkqtExcelEngine.display("迟到"));}
    @Test void combined_status_text_labels_both_halves(){assertEquals("上午：出勤\n下午：事假",ZsjkqtExcelEngine.combined("出勤","事假"));assertEquals("上午：--\n下午：--",ZsjkqtExcelEngine.combined("",""));}
    @Test void only_plain_statuses_use_normal_text_style(){for(String s:List.of("出勤","休息","带薪","调休","加班"))assertEquals("normal",ZsjkqtExcelEngine.styleKey(s));for(String s:List.of("迟到","缺卡","事假","病假","工伤","丧假","婚假","产假","年假"))assertNotEquals("normal",ZsjkqtExcelEngine.styleKey(s));}
    @Test void notable_statuses_use_separate_style_keys(){String[] states={"迟到","缺卡","事假","病假","工伤","丧假","婚假","产假","年假","早退","异常","旷工"},keys={"late","missing","personal","benefit","benefit","benefit","benefit","benefit","benefit","early","abnormal","absence"};for(int i=0;i<states.length;i++)assertEquals(keys[i],ZsjkqtExcelEngine.styleKey(states[i]));}
    @Test void non_plain_status_styles_have_background_fills(){for(String s:ZsjkqtExcelEngine.STATUSES){String key=ZsjkqtExcelEngine.styleKey(s);assertEquals(key.equals("normal"),ZsjkqtExcelEngine.background(key)==null);}}
    @Test void combined_status_style_uses_most_notable_half(){String[][] pairs={{"出勤","异常","abnormal"},{"迟到","早退","late"},{"出勤","休息","normal"},{"年假","出勤","benefit"},{"病假","出勤","benefit"},{"年假","迟到","late"}};for(var p:pairs)assertEquals(p[2],ZsjkqtExcelEngine.pairStyle(p[0],p[1]));}
    @Test void employees_are_grouped_by_first_seen_department_order(){var p=json("""
      {"employees":[{"displayName":"张三","displayDepartment":"教务部","sourceIndex":1},{"displayName":"李四","displayDepartment":"财务部","sourceIndex":2},{"displayName":"王五","displayDepartment":"教务部","sourceIndex":3},{"displayName":"赵六","displayDepartment":"财务部","sourceIndex":4,"enabled":false}]}
      """);assertEquals(List.of("张三","王五","李四"),ZsjkqtExcelEngine.sortedEmployees(p).stream().map(e->e.path("displayName").asText()).toList());}
    @Test void blank_manual_status_change_is_not_exported_as_issue(){assertFalse(ZsjkqtExcelEngine.shouldExportIssue(json("{\"finalMorning\":\"出勤\",\"finalAfternoon\":\"事假\"}"),0));}
    @Test void blank_statuses_are_not_exported_even_when_system_issues_exist(){assertFalse(ZsjkqtExcelEngine.shouldExportIssue(json("{}"),1));}
    @Test void system_abnormal_issue_is_exported(){assertTrue(ZsjkqtExcelEngine.shouldExportIssue(json("{\"systemMorning\":\"异常\",\"finalMorning\":\"异常\"}"),1));}
    @Test void manual_abnormal_status_change_is_exported(){assertTrue(ZsjkqtExcelEngine.shouldExportIssue(json("{\"systemMorning\":\"出勤\",\"finalMorning\":\"异常\"}"),0));}

    @Test void parses_sample_wecom_workbook_from_file() throws Exception {
        Path file=Path.of("../../docs/testing/zsjkqt/original/上下班打卡_月报_20260501-20260531 (1).xlsx");assertTrue(Files.exists(file));JsonNode parsed=JsonUtils.getObjectMapper().valueToTree(engine.parseSource(Files.readAllBytes(file),file.getFileName().toString()));assertEquals(2026,parsed.path("attendance").path("year").asInt());assertEquals(5,parsed.path("attendance").path("month").asInt());assertEquals(39,parsed.path("attendance").path("employees").size());assertEquals(31,parsed.path("attendance").path("employees").get(0).path("dayDetails").size());
    }
    byte[] report(boolean metadata,String sheetName) throws Exception {
        try(var b=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {var s=b.createSheet(sheetName);var h=s.createRow(0);String[] head={"序号","部门","姓名","考勤"};for(int i=0;i<4;i++)h.createCell(i).setCellValue(head[i]);for(int d=1;d<=30;d++)h.createCell(3+d).setCellValue(ZsjkqtExcelEngine.dateHeader(2026,6,d));for(int i=0;i<ZsjkqtExcelEngine.TITLES.length;i++)h.createCell(34+i).setCellValue(ZsjkqtExcelEngine.TITLES[i]);var row=s.createRow(1);row.createCell(1).setCellValue("教务部");row.createCell(2).setCellValue("张三");for(int d=1;d<=30;d++)row.createCell(3+d).setCellValue(ZsjkqtExcelEngine.combined(d==1?"出勤":d==2?"休息":"",d==1?"事假":d==2?"休息":""));row.createCell(34).setCellValue(21);row.createCell(35).setCellValue(99);row.createCell(36).setCellValue(23);row.createCell(53).setCellValue("原表备注");
            if(metadata){var meta=b.createSheet(ZsjkqtExcelEngine.METADATA);String[][] values={{"appName","zhongshijian-kaoqintong"},{"schemaVersion","1"},{"year","2026"},{"month","6"},{"shouldAttendanceDays","20"},{"payrollDays","22"},{"restDays","6,13"},{"paidRestDays","1"}};for(int i=0;i<values.length;i++){var r=meta.createRow(i);r.createCell(0).setCellValue(values[i][0]);r.createCell(1).setCellValue(values[i][1]);}}
            b.write(out);return out.toByteArray();}
    }
    @Test void parses_strict_exported_report_without_recalculating_values() throws Exception {JsonNode p=JsonUtils.getObjectMapper().valueToTree(engine.parseReport(report(false,"2026年6月考勤"),"报告.xlsx"));assertEquals(99,p.path("employees").get(0).path("summary").path("actualAttendanceDays").asInt());assertEquals("原表备注",p.path("employees").get(0).path("summary").path("remark").asText());assertEquals("inferred",p.path("recoveredParameters").path("source").asText());assertEquals(21,p.path("recoveredParameters").path("shouldAttendanceDays").asInt());assertEquals(2,p.path("recoveredParameters").path("restDayCandidates").get(0).path("day").asInt());}
    @Test void rejects_old_exported_sheets_without_year() throws Exception {assertThrows(IllegalArgumentException.class,()->engine.parseReport(report(false,"6月考勤"),"报告.xlsx"));}
    @Test void uses_hidden_metadata_when_present() throws Exception {JsonNode p=JsonUtils.getObjectMapper().valueToTree(engine.parseReport(report(true,"2026年6月考勤"),"报告.xlsx")).path("recoveredParameters");assertEquals("metadata",p.path("source").asText());assertEquals(20,p.path("shouldAttendanceDays").asInt());assertEquals(22,p.path("payrollDays").asInt());assertEquals(json("[6,13]"),p.path("restDays"));assertEquals(json("[1]"),p.path("paidRestDays"));}
    @ParameterizedTest @ValueSource(ints={2,6,12}) void exports_all_calendar_days_even_missing_from_snapshot(int month) throws Exception {
        var p=(tools.jackson.databind.node.ObjectNode)project();p.put("month",month);byte[] bytes=engine.exportReport(p);assertDoesNotThrow(()->engine.parseReport(bytes,"测试.xlsx"));
    }
    @Test void issue_export_preserves_original_filter_and_disabled_people() throws Exception {var p=json("""
      {"employees":[{"displayName":"合成甲","displayDepartment":"合成部门","enabled":false,"days":[{"day":1,"systemMorning":"异常","finalMorning":"出勤","issues":[{"message":"缺卡"}]},{"day":2,"systemMorning":"出勤","finalMorning":"事假","issues":[{"message":"人工修改，汇总数据待核对"}]}]}]}
      """);try(var b=new XSSFWorkbook(new ByteArrayInputStream(engine.exportIssues(p)))){assertEquals(2,b.getSheetAt(0).getLastRowNum());assertEquals("未导出人员",b.getSheetAt(0).getRow(1).getCell(4).getStringCellValue());assertEquals("缺卡",b.getSheetAt(0).getRow(2).getCell(4).getStringCellValue());}}
}
