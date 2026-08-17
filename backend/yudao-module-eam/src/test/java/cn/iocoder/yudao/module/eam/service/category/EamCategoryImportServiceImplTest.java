package cn.iocoder.yudao.module.eam.service.category;

import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryImportRespVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EamCategoryImportServiceImplTest {

    @InjectMocks
    private EamCategoryImportServiceImpl importService;
    @Mock
    private EamCategoryService categoryService;
    @Mock
    private EamCategoryFieldService fieldService;

    @BeforeEach
    void setUp() {
        when(categoryService.getCategoryList()).thenReturn(List.of());
    }

    @Test
    void preview_shouldReportCreatesWithoutDeletingExistingData() throws Exception {
        EamCategoryImportRespVO result = importService.preview(workbook(""));

        assertEquals(3, result.getCreateCount());
        assertEquals(0, result.getUpdateCount());
        assertEquals(0, result.getSkipCount());
        assertEquals(0, result.getConflictCount());
    }

    @Test
    void preview_shouldRejectNonObjectConditionRule() throws Exception {
        EamCategoryImportRespVO result = importService.preview(workbook("[1,2]"));

        assertEquals(1, result.getConflictCount());
        assertEquals("条件规则必须是 JSON 对象", result.getItems().get(2).getMessage());
    }

    private static byte[] workbook(String conditionRule) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet categories = workbook.createSheet("分类");
            Row categoryHeader = categories.createRow(0);
            for (int index = 0; index < 8; index++) set(categoryHeader, index, "H" + index);
            Row root = categories.createRow(1);
            set(root, 0, "IT"); set(root, 1, "IT硬件设备"); set(root, 3, "开启");
            set(root, 4, "1"); set(root, 5, "单件"); set(root, 6, "个");
            Row child = categories.createRow(2);
            set(child, 0, "IT-001"); set(child, 1, "笔记本"); set(child, 2, "IT");
            set(child, 3, "开启"); set(child, 4, "1"); set(child, 5, "单件"); set(child, 6, "个");

            Sheet fields = workbook.createSheet("字段");
            Row fieldHeader = fields.createRow(0);
            for (int index = 0; index < 10; index++) set(fieldHeader, index, "H" + index);
            Row field = fields.createRow(1);
            set(field, 0, "IT"); set(field, 1, "cpu"); set(field, 2, "处理器");
            set(field, 3, "单行文本"); set(field, 5, "是"); set(field, 6, "是");
            set(field, 7, "否"); set(field, 8, conditionRule); set(field, 9, "1");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void set(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

}
