package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EamAssetLedgerParserTest {

    private final EamAssetLedgerParser parser = new EamAssetLedgerParser();

    @Test
    void parse_shouldUseHeaderNamesAndFieldKeysWithoutExposingPassword() throws Exception {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("cpu:CPU", "M4 Pro");
        values.put("资产名称", "MacBook Pro");
        values.put("微信密码", "TOP_SECRET_PASSWORD");
        values.put("购入日期", "2026年6月");
        values.put("分类编码", "IT-COMPUTER");
        values.put("资产状态", "在用");
        values.put("数量", "1");
        values.put("资产编号", "ZSJ-001");

        EamAssetLedgerParser.LedgerRow actual = parser.parse(workbook(values)).get(0);

        assertEquals(2, actual.rowNum());
        assertEquals("IT-COMPUTER", actual.categoryCode());
        assertEquals("MacBook Pro", actual.assetName());
        assertEquals(EamAssetStatusEnum.IN_USE.getStatus(), actual.status());
        assertEquals(LocalDate.of(2026, 6, 1), actual.purchaseDate());
        assertEquals("M4 Pro", actual.extFields().get("cpu"));
        assertFalse(actual.extFields().toString().contains("TOP_SECRET_PASSWORD"));
        assertFalse(actual.mappedFields().toString().contains("TOP_SECRET_PASSWORD"));
    }

    @Test
    void parse_shouldReportMissingCategoryCodeAsRowError() throws Exception {
        EamAssetLedgerParser.LedgerRow actual = parser.parse(
                workbook(Map.of("资产名称", "未分类资产", "分类编码", ""))).get(0);
        assertTrue(actual.errors().contains("分类编码为空"));
    }

    @Test
    void parse_shouldRejectWorkbookWithoutRequiredHeaders() throws Exception {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> parser.parse(workbook(Map.of("资产名称", "缺分类表头"))));
        assertTrue(exception.getMessage().contains("分类编码和资产名称"));
    }

    @Test
    void parse_shouldReportInvalidStandardFieldTypes() throws Exception {
        EamAssetLedgerParser.LedgerRow actual = parser.parse(workbook(Map.of(
                "分类编码", "BOOK", "资产名称", "教材", "原值", "十二元",
                "预计使用年限（月）", "1.5", "保修到期日", "待确认"))).get(0);
        assertEquals(3, actual.errors().size());
    }

    @Test
    void parseDate_shouldSupportDayMonthAndYearPrecision() {
        assertEquals(LocalDate.of(2026, 6, 23), EamAssetLedgerParser.parseDate("2026/6/23"));
        assertEquals(LocalDate.of(2026, 6, 1), EamAssetLedgerParser.parseDate("2026.6"));
        assertEquals(LocalDate.of(2026, 1, 1), EamAssetLedgerParser.parseDate("2026年"));
        assertEquals(null, EamAssetLedgerParser.parseDate("待确认"));
    }

    private static byte[] workbook(Map<String, String> values) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(EamAssetLedgerParser.SHEET_NAME);
            Row header = sheet.createRow(0);
            Row data = sheet.createRow(1);
            int index = 0;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                header.createCell(index).setCellValue(entry.getKey());
                data.createCell(index).setCellValue(entry.getValue());
                index++;
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
