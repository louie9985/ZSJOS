package cn.iocoder.yudao.framework.excel.core.handler;

import cn.iocoder.yudao.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.yudao.framework.excel.core.annotations.ExcelColumnSelect;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class SelectSheetWriteHandlerTest {
    @Test
    void emptyDictionaryDoesNotBreakOtherDropdowns() throws Exception {
        try (MockedStatic<DictFrameworkUtils> dictionaries = mockStatic(DictFrameworkUtils.class)) {
            dictionaries.when(() -> DictFrameworkUtils.getDictDataLabelList("empty")).thenReturn(List.of());
            dictionaries.when(() -> DictFrameworkUtils.getDictDataLabelList("available")).thenReturn(List.of("正常", "停用"));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            FastExcelFactory.write(output, Row.class).registerWriteHandler(new SelectSheetWriteHandler(Row.class))
                    .sheet().doWrite(List.of());
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
                assertNull(workbook.getName("dict0"));
                assertEquals("字典sheet!$B$1:$B$2", workbook.getName("dict1").getRefersToFormula());
                assertEquals(1, workbook.getSheetAt(0).getDataValidations().size());
            }
        }
    }

    @Data
    public static class Row {
        @ExcelProperty("空字典") @ExcelColumnSelect(dictType = "empty")
        private String empty;
        @ExcelProperty("可用字典") @ExcelColumnSelect(dictType = "available")
        private String available;
    }
}
