package cn.iocoder.yudao.module.pms.service.kb.content;

import org.junit.jupiter.api.Test;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

class PmsKnowledgeExcelParserTest {
    private final PmsKnowledgeExcelParser parser = new PmsKnowledgeExcelParser();
    private byte[] bytes(Workbook book) throws Exception {
        var out = new ByteArrayOutputStream(); book.write(out); return out.toByteArray();
    }
    @Test void realXlsAndXlsxDisplayFormatsAndCachedFormula() throws Exception {
        for (Workbook book : new Workbook[]{new HSSFWorkbook(), new XSSFWorkbook()}) {
            try (book) {
                var row = book.createSheet("培训中文").createRow(0);
                row.createCell(0).setCellValue("<script>中文</script>");
                row.createCell(1).setCellValue(.125);
                var percent = book.createCellStyle(); percent.setDataFormat(book.createDataFormat().getFormat("0.0%"));
                row.getCell(1).setCellStyle(percent);
                row.createCell(2).setCellValue(java.time.LocalDateTime.of(2026,10,18,0,0));
                var date = book.createCellStyle(); date.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
                row.getCell(2).setCellStyle(date);
                row.createCell(3).setCellFormula("1+2");
                book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(row.getCell(3));
                book.createSheet("空表");
                book.createSheet("隐藏"); book.setSheetHidden(2,true);
                var result = parser.parse(bytes(book),-1);
                assertEquals(2,result.sheets().size());
                assertEquals(java.util.List.of("<script>中文</script>","12.5%","2026-10-18","3"),result.rows().getFirst());
                assertTrue(parser.parse(bytes(book),1).rows().isEmpty());
                assertServiceException(() -> parser.parse(bytes(book),2),KNOWLEDGE_EXCEL_SHEET_INVALID);
            }
        }
    }
    @Test void missingCacheAndTruncation() throws Exception {
        try(var book=new XSSFWorkbook()) {
            var sheet=book.createSheet("表格");
            sheet.createRow(0).createCell(0).setCellFormula("1+2");
            sheet.createRow(500).createCell(0).setCellValue("不可显示");
            sheet.getRow(0).createCell(100).setCellValue("不可显示");
            var result=parser.parse(bytes(book),-1);
            assertEquals("[公式结果未保存]",result.rows().getFirst().getFirst());
            assertEquals(500,result.rows().size()); assertEquals(100,result.columnCount());
            assertTrue(result.truncated()); assertEquals(2,result.notices().size());
        }
    }
    @Test void rejectCorruptOversizedAndExpandedArchives() throws Exception {
        assertServiceException(() -> parser.parse(new byte[]{1,2,3},-1), KNOWLEDGE_EXCEL_INVALID);
        assertServiceException(() -> parser.parse(new byte[(int)PmsKnowledgeExcelParser.MAX_BYTES+1],-1), KNOWLEDGE_EXCEL_TOO_LARGE);
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out)) { zip.putNextEntry(new ZipEntry("large.xml")); zip.write(new byte[2*1024*1024]); }
        assertServiceException(() -> parser.parse(out.toByteArray(),-1), KNOWLEDGE_EXCEL_TOO_LARGE);
    }
    @Test void rejectEncryptedXls() throws Exception {
        try(var book=new HSSFWorkbook()) {
            book.createSheet("保护");
            org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword("fixture-password");
            byte[] encrypted;
            try { encrypted=bytes(book); }
            finally { org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(null); }
            assertServiceException(() -> parser.parse(encrypted,-1),KNOWLEDGE_EXCEL_ENCRYPTED);
        }
    }
    @Test void mergedCellsPreserveSparseExtentsAndClipAtPreviewBoundary() throws Exception {
        for (Workbook book : new Workbook[]{new HSSFWorkbook(), new XSSFWorkbook()}) {
            try (book) {
                var sheet = book.createSheet("合并");
                sheet.createRow(0).createCell(0).setCellValue("横向标题");
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,0,3));
                sheet.createRow(1).createCell(0).setCellValue("纵向");
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1,3,0,0));
                sheet.getRow(1).createCell(1).setCellValue("矩形");
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1,3,1,3));
                var result = parser.parse(bytes(book), -1);
                assertEquals(4,result.rows().size()); assertEquals(4,result.columnCount());
                assertEquals(3,result.merges().size());
                assertEquals(3,result.merges().get(2).rowSpan());
                assertEquals(3,result.merges().get(2).colSpan());
                assertEquals("横向标题",result.rows().getFirst().getFirst());
                sheet.createRow(498).createCell(98).setCellValue("边界");
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(498,502,98,102));
                result = parser.parse(bytes(book), -1);
                assertEquals(500,result.rows().size()); assertEquals(100,result.columnCount());
                assertEquals(2,result.merges().getLast().rowSpan());
                assertEquals(2,result.merges().getLast().colSpan()); assertTrue(result.truncated());
            }
        }
    }
}
