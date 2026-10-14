package cn.iocoder.yudao.framework.excel.core.convert;

import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaConvertTest {
    @Test
    void templateWritesAnImportableAreaPath() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Row row = new Row();
        row.setArea(330100);
        FastExcelFactory.write(output, Row.class).sheet().doWrite(List.of(row));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            String path = workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue();
            assertEquals(330100, AreaUtils.parseArea(path).getId());
            assertTrue(path.contains("/"));
        }
    }

    @Test
    void emptyAreaWritesBlankAndInvalidAreaIsNotInvented() throws Exception {
        AreaConvert converter = new AreaConvert();
        assertEquals("", converter.convertToExcelData(null, null, null).getStringValue());
        assertThrows(IllegalArgumentException.class, () -> converter.convertToExcelData(-1, null, null));
    }

    @Data
    public static class Row {
        @ExcelProperty(value = "开户地区", converter = AreaConvert.class)
        private Integer area;
    }
}
