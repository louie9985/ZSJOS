package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EamAssetLedgerParserTest {

    private final EamAssetLedgerParser parser = new EamAssetLedgerParser();

    @Test
    void parse_shouldMapDualHeaderAndNeverExposeCredential() throws Exception {
        byte[] content = workbook(List.of(row -> {
            set(row, 0, "张三");
            set(row, 3, "C栋24楼");
            set(row, 4, "IT硬件设备");
            set(row, 8, "其他IT硬件设备");
            set(row, 9, "会议终端");
            set(row, 18, "品牌 A");
            set(row, 19, "SN-001");
            set(row, 25, "2026年6月");
            set(row, 36, "TOP_SECRET_PASSWORD");
            set(row, 48, "ZSJ-001");
            set(row, 50, "附件.jpg");
        }));

        EamAssetLedgerParser.LedgerRow actual = parser.parse(content).get(0);

        assertEquals(3, actual.rowNum());
        assertEquals("IT硬件设备", actual.rootCategoryName());
        assertEquals("其他IT硬件设备", actual.leafCategoryName());
        assertEquals("会议终端", actual.assetName());
        assertEquals(1, actual.quantity());
        assertEquals(EamAssetStatusEnum.IDLE.getStatus(), actual.status());
        assertEquals(LocalDate.of(2026, 6, 1), actual.purchaseDate());
        assertTrue(actual.defaultedFields().contains("数量为空，默认按 1 导入"));
        assertTrue(actual.defaultedFields().contains("使用状态为空，默认按闲置导入"));
        assertFalse(actual.extFields().toString().contains("TOP_SECRET_PASSWORD"));
        assertFalse(actual.sourceFields().toString().contains("TOP_SECRET_PASSWORD"));
        assertFalse(actual.extFields().containsKey("wechat_password"));
    }

    @Test
    void parse_shouldUseStableOtherLeafAndDescriptionAsName() throws Exception {
        byte[] content = workbook(List.of(row -> {
            set(row, 4, "其他");
            set(row, 5, "企业文化纪念品");
            set(row, 16, "2");
            set(row, 43, "正常使用中");
        }));

        EamAssetLedgerParser.LedgerRow actual = parser.parse(content).get(0);

        assertEquals("其他资产", actual.leafCategoryName());
        assertEquals("企业文化纪念品", actual.assetName());
        assertEquals(2, actual.quantity());
        assertEquals(EamAssetStatusEnum.IN_USE.getStatus(), actual.status());
    }

    @Test
    void parse_shouldReportActualExcelRowForUnknownCategory() throws Exception {
        byte[] content = workbook(List.of(row -> set(row, 4, "未知分类")));

        ServiceException exception = assertThrows(ServiceException.class, () -> parser.parse(content));

        assertTrue(exception.getMessage().contains("第 3 行资产大类无法识别"));
    }

    @Test
    void parseDate_shouldSupportDayMonthAndYearPrecision() {
        assertEquals(LocalDate.of(2026, 6, 23), EamAssetLedgerParser.parseDate("2026/6/23"));
        assertEquals(LocalDate.of(2026, 6, 1), EamAssetLedgerParser.parseDate("2026.6"));
        assertEquals(LocalDate.of(2026, 1, 1), EamAssetLedgerParser.parseDate("2026年"));
        assertEquals(null, EamAssetLedgerParser.parseDate("待确认"));
    }

    private static byte[] workbook(List<java.util.function.Consumer<Row>> dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(EamAssetLedgerParser.SHEET_NAME);
            sheet.createRow(0).createCell(0).setCellValue("责任人信息");
            Row header = sheet.createRow(1);
            set(header, 4, "资产大类");
            set(header, 48, "资产标签");
            for (int index = 0; index < dataRows.size(); index++) {
                Row row = sheet.createRow(index + 2);
                dataRows.get(index).accept(row);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void set(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

}
