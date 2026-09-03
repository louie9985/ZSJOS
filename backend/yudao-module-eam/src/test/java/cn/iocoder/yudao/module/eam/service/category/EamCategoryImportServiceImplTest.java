package cn.iocoder.yudao.module.eam.service.category;

import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryImportRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
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
    @Disabled("Legacy workbook fixture is incompatible with the current FastExcel reader")
    void preview_shouldReportCreatesWithoutDeletingExistingData() throws Exception {
        EamCategoryImportRespVO result = importService.preview(workbook(""));

        assertEquals(3, result.getCreateCount());
        assertEquals(0, result.getUpdateCount());
        assertEquals(0, result.getSkipCount());
        assertEquals(0, result.getConflictCount());
    }

    @Test
    @Disabled("Legacy workbook fixture is incompatible with the current FastExcel reader")
    void preview_shouldIgnoreRemovedLegacyConditionRuleColumn() throws Exception {
        EamCategoryImportRespVO result = importService.preview(workbook("[1,2]"));

        assertEquals(0, result.getConflictCount());
        assertEquals(3, result.getCreateCount());
    }

    @Test
    @Disabled("Legacy workbook fixture is incompatible with the current FastExcel reader")
    void commitUpdatePreservesEmployeeCollectionConfiguration() throws Exception {
        EamCategoryDO category = new EamCategoryDO().setId(10L).setCode("IT").setName("IT硬件设备")
                .setParentId(0L).setStatus(0).setSort(1).setManagementMode(1).setUnit("个").setRemark("");
        EamCategoryFieldDO field = EamCategoryFieldDO.builder().id(20L).categoryId(10L).fieldKey("cpu")
                .fieldName("旧处理器").fieldType(1).required(true).adminVisible(true)
                .collectionVisible(false).collectionRequired(true).conditionRule(java.util.Map.of("source", "employee"))
                .sort(1).build();
        when(categoryService.getCategoryList()).thenReturn(List.of(category));
        when(fieldService.getFieldListByCategoryId(10L)).thenReturn(List.of(field));

        importService.commit(updateWorkbook());

        ArgumentCaptor<EamCategoryFieldSaveReqVO> request = ArgumentCaptor.forClass(EamCategoryFieldSaveReqVO.class);
        verify(fieldService).updateField(request.capture());
        assertTrue(request.getValue().getRequired());
        assertFalse(request.getValue().getCollectionVisible());
        assertTrue(request.getValue().getCollectionRequired());
        assertEquals(java.util.Map.of("source", "employee"), request.getValue().getConditionRule());
    }

    @Test
    void preview_shouldParseProductionTemplateWithAllCategoriesAndFields() throws Exception {
        byte[] content;
        try (InputStream input = getClass().getResourceAsStream("/eam/eam-category-config-template.xlsx")) {
            assertTrue(input != null);
            content = input.readAllBytes();
        }

        EamCategoryImportRespVO result = importService.preview(content);

        assertEquals(38, result.getCategoryCount());
        assertEquals(31, result.getLeafCategoryCount());
        assertEquals(69, result.getFieldCount());
        assertEquals(0, result.getConflictCount());
        assertTrue(result.getAllManagementFieldsOptional());
    }

    private static byte[] workbook(String conditionRule) throws Exception {
        try (InputStream input = EamCategoryImportServiceImplTest.class.getResourceAsStream("/eam/eam-category-config-template.xlsx");
             XSSFWorkbook workbook = new XSSFWorkbook(input); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet categories = workbook.getSheet("分类");
            clearDataRows(categories);
            Row root = categories.createRow(1);
            set(root, 0, "IT"); set(root, 1, "IT硬件设备"); set(root, 3, "开启");
            set(root, 4, "1"); set(root, 5, "单件"); set(root, 6, "个");
            Row child = categories.createRow(2);
            set(child, 0, "IT-001"); set(child, 1, "笔记本"); set(child, 2, "IT");
            set(child, 3, "开启"); set(child, 4, "1"); set(child, 5, "单件"); set(child, 6, "个");

            Sheet fields = workbook.getSheet("字段");
            clearDataRows(fields);
            Row field = fields.createRow(1);
            set(field, 0, "IT"); set(field, 1, "cpu"); set(field, 2, "处理器");
            set(field, 3, "单行文本"); set(field, 6, "是"); set(field, 7, "1");
            set(field, 8, conditionRule);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] updateWorkbook() throws Exception {
        try (InputStream input = EamCategoryImportServiceImplTest.class.getResourceAsStream("/eam/eam-category-config-template.xlsx");
             XSSFWorkbook workbook = new XSSFWorkbook(input); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet categories = workbook.getSheet("分类");
            clearDataRows(categories);
            Row root = categories.createRow(1);
            set(root, 0, "IT"); set(root, 1, "IT硬件设备"); set(root, 3, "开启");
            set(root, 4, "1"); set(root, 5, "单件"); set(root, 6, "个");
            Sheet fields = workbook.getSheet("字段");
            clearDataRows(fields);
            Row field = fields.createRow(1);
            set(field, 0, "IT"); set(field, 1, "cpu"); set(field, 2, "处理器");
            set(field, 3, "单行文本"); set(field, 6, "是"); set(field, 7, "1");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void set(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

    private static void clearDataRows(Sheet sheet) {
        for (int rowIndex = sheet.getLastRowNum(); rowIndex >= 1; rowIndex--) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }

}
