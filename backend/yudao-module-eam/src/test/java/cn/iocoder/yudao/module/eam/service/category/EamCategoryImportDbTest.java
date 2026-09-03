package cn.iocoder.yudao.module.eam.service.category;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import cn.iocoder.yudao.module.eam.dal.mysql.category.EamCategoryFieldMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.category.EamCategoryMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryImportRespVO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({EamCategoryImportServiceImpl.class, EamCategoryServiceImpl.class, EamCategoryFieldServiceImpl.class})
class EamCategoryImportDbTest extends BaseDbUnitTest {

    @Resource
    private EamCategoryImportService importService;
    @Resource
    private EamCategoryMapper categoryMapper;
    @Resource
    private EamCategoryFieldMapper fieldMapper;
    @MockitoBean
    private EamAssetMapper assetMapper;
    @MockitoBean
    private DictDataApi dictDataApi;

    @Test
    void commit_shouldPopulateEmptyDatabaseFromProductionTemplate() throws Exception {
        byte[] content = templateContent();

        EamCategoryImportRespVO result = importService.commit(content);

        assertEquals(38, result.getCategoryCount());
        assertEquals(107, result.getCreateCount());
        assertEquals(69, result.getFieldCount());
        assertEquals(38, categoryMapper.selectList().size());
        assertEquals(69, fieldMapper.selectCount());
        Set<Long> categoryIds = categoryMapper.selectList().stream().map(category -> category.getId()).collect(Collectors.toSet());
        assertEquals(7, categoryMapper.selectList().stream().filter(category -> category.getParentId() == 0L).count());
        assertEquals(31, categoryMapper.selectList().stream().filter(category -> category.getParentId() != 0L).count());
        assertTrue(categoryMapper.selectList().stream().filter(category -> category.getParentId() != 0L)
                .allMatch(category -> categoryIds.contains(category.getParentId())));
        assertTrue(categoryMapper.selectList().stream().allMatch(c -> c.getDeliveryMode() == 1 && c.getCustodyMode() == 1));
        List<EamCategoryFieldDO> files = fieldMapper.selectList().stream().filter(f -> Integer.valueOf(6).equals(f.getFieldType())).toList();
        assertTrue(files.size() > 0);
        assertTrue(fieldMapper.selectList().stream().allMatch(f -> !Boolean.TRUE.equals(f.getRequired())));

        EamCategoryImportRespVO repeated = importService.commit(content);
        assertEquals(0, repeated.getCreateCount());
        assertEquals(0, repeated.getUpdateCount());
        assertEquals(107, repeated.getSkipCount());
        assertEquals(38, categoryMapper.selectList().size());
        assertEquals(69, fieldMapper.selectCount());
    }

    @Test
    void commit_shouldResolveNewParentWhenChildAppearsFirst() throws Exception {
        byte[] content = templateContent();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet categories = workbook.getSheet("分类");
            swapRows(categories, findRow(categories, "DIGITAL"), findRow(categories, "DIGITAL-PLATFORM"));
            workbook.write(output);
            content = output.toByteArray();
        }

        EamCategoryImportRespVO result = importService.commit(content);

        assertEquals(0, result.getConflictCount());
        assertEquals(38, categoryMapper.selectList().size());
        assertEquals(69, fieldMapper.selectCount());
    }

    private byte[] templateContent() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/eam/eam-category-config-template.xlsx")) {
            assertTrue(input != null);
            return input.readAllBytes();
        }
    }

    private static int findRow(Sheet sheet, String categoryCode) {
        DataFormatter formatter = new DataFormatter();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            if (categoryCode.equals(formatter.formatCellValue(sheet.getRow(rowIndex).getCell(0)))) {
                return rowIndex;
            }
        }
        throw new IllegalArgumentException("Category not found: " + categoryCode);
    }

    private static void swapRows(Sheet sheet, int firstIndex, int secondIndex) {
        DataFormatter formatter = new DataFormatter();
        Row first = sheet.getRow(firstIndex);
        Row second = sheet.getRow(secondIndex);
        for (int column = 0; column < 10; column++) {
            String firstValue = formatter.formatCellValue(first.getCell(column));
            String secondValue = formatter.formatCellValue(second.getCell(column));
            first.getCell(column).setCellValue(secondValue);
            second.getCell(column).setCellValue(firstValue);
        }
    }
}
