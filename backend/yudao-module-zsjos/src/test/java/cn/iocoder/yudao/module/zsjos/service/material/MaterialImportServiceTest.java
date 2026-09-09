package cn.iocoder.yudao.module.zsjos.service.material;

import cn.hutool.crypto.digest.DigestUtil;
import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.write.metadata.WriteSheet;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialImportBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialImportErrorDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialImportBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialImportErrorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_ATTACHMENT;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_DATE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_DATETIME;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_DICT_MULTI;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_EMPLOYEE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_NUMBER;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_REPEAT_GROUP;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.SCHEMA_PUBLISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialImportServiceTest {

    @InjectMocks private MaterialImportService service;
    @Mock private MaterialTypeService materialTypeService;
    @Mock private MaterialSchemaService schemaService;
    @Mock private MaterialService materialService;
    @Mock private MaterialImportBatchMapper batchMapper;
    @Mock private MaterialImportErrorMapper errorMapper;

    private MaterialTypeDO type;
    private MaterialSchemaVersionDO schema;
    private final AtomicReference<MaterialImportBatchDO> savedBatch = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        type = new MaterialTypeDO().setId(11L).setName("爆款拆解").setCode("viral_content")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setAllowImport(true)
                .setCurrentSchemaVersionId(21L);
        schema = new MaterialSchemaVersionDO().setId(21L).setMaterialTypeId(11L).setVersionNo(3)
                .setStatus(SCHEMA_PUBLISHED).setFieldsJson("[]").setSchemaHash("schema-hash");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void generatedTemplateCanBeReadWithExpectedSheets() {
        stubTemplate();
        MaterialFieldDefinition metric = field("metric", "互动量", FIELD_NUMBER);
        MaterialFieldDefinition cases = field("cases", "案例", FIELD_REPEAT_GROUP);
        cases.setChildren(List.of(metric));
        when(schemaService.parseFields("[]")).thenReturn(List.of(cases));

        MaterialImportService.WorkbookFile workbook = service.buildTemplate(11L);

        assertTrue(workbook.fileName().endsWith(".xlsx"));
        List<Map<Integer, String>> mainRows = read(workbook.content(), "素材");
        assertEquals("导入行标识[__row_key__]", mainRows.getFirst().get(0));
        String groupSheet = "组-cases-" + DigestUtil.sha256Hex("cases").substring(0, 6);
        List<Map<Integer, String>> groupRows = read(workbook.content(), groupSheet);
        assertEquals("互动量[cases.metric]", groupRows.getFirst().get(1));
        assertEquals("模板哈希", read(workbook.content(), "模板信息").get(5).get(0));
    }

    @Test
    void invalidRepeatGroupRowFailsItsMainMaterialAndKeepsPreviewReport() {
        stubTemplate();
        MaterialFieldDefinition metric = field("metric", "互动量", FIELD_NUMBER);
        MaterialFieldDefinition cases = field("cases", "案例", FIELD_REPEAT_GROUP);
        cases.setChildren(List.of(metric));
        when(schemaService.parseFields("[]")).thenReturn(List.of(cases));
        stubBatchPersistence();
        byte[] content = workbook(List.of(
                sheet("模板信息", List.of(List.of("配置项", "值")), infoRows()),
                sheet("素材", List.of(List.of("导入行标识[__row_key__]"), List.of("标题[__title__]"),
                                List.of("封面文件ID[__cover_file_id__]"), List.of("摘要[__summary__]")),
                        List.of(List.of("row-a", "素材 A", "", ""))),
                sheet("组-cases-" + DigestUtil.sha256Hex("cases").substring(0, 6),
                        List.of(List.of("导入行标识[__row_key__]"), List.of("互动量[cases.metric]")),
                        List.of(List.of("row-a", "不是数字")))));

        var result = service.preview(11L, "import.xlsx", content, "preview-1", 7L);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        verify(materialService, never()).validateImport(anyLong(), anyLong(), any(), anyLong());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialImportErrorDO>> errors = ArgumentCaptor.forClass(List.class);
        verify(errorMapper).insertBatch(errors.capture());
        assertTrue(errors.getValue().stream().anyMatch(error -> "CELL_FORMAT".equals(error.getErrorCode())));
        assertTrue(errors.getValue().stream().anyMatch(error -> "GROUP_ROW_INVALID".equals(error.getErrorCode())));
    }

    @Test
    void multiValueAndDateCellsAreNormalizedBeforeBusinessValidation() {
        stubTemplate();
        MaterialFieldDefinition audiences = field("audiences", "目标客资", FIELD_DICT_MULTI);
        MaterialFieldDefinition owners = field("owners", "负责人", FIELD_EMPLOYEE);
        owners.setMultiple(true);
        MaterialFieldDefinition publishDate = field("publish_date", "发布日期", FIELD_DATE);
        MaterialFieldDefinition publishAt = field("publish_at", "发布时间", FIELD_DATETIME);
        MaterialFieldDefinition attachments = field("attachments", "附件", FIELD_ATTACHMENT);
        List<MaterialFieldDefinition> fields = List.of(audiences, owners, publishDate, publishAt, attachments);
        when(schemaService.parseFields("[]")).thenReturn(fields);
        stubBatchPersistence();
        byte[] content = workbook(List.of(
                sheet("模板信息", List.of(List.of("配置项", "值")), infoRows()),
                sheet("素材", mainHeaders(fields), List.of(List.of("row-a", "素材 A", "", "摘要",
                        "lead|customer|lead", "7|8", "2026-09-08", "2026-09-08 09:30", "11|12")))));

        var result = service.preview(11L, "import.xlsx", content, "preview-2", 7L);

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<MaterialSaveReqVO> request = ArgumentCaptor.forClass(MaterialSaveReqVO.class);
        verify(materialService).validateImport(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(21L), request.capture(), org.mockito.ArgumentMatchers.eq(7L));
        assertEquals(List.of("lead", "customer"), request.getValue().getValues().get("audiences"));
        assertEquals(List.of(7L, 8L), request.getValue().getValues().get("owners"));
        assertEquals("2026-09-08", request.getValue().getValues().get("publish_date"));
        assertEquals("2026-09-08T09:30", request.getValue().getValues().get("publish_at"));
        assertEquals(List.of(11L, 12L), request.getValue().getValues().get("attachments"));
    }

    @Test
    void commitRejectsUserWhoDidNotCreateTheBatch() {
        MaterialSaveReqVO request = new MaterialSaveReqVO().setMaterialTypeId(11L).setTitle("素材 A")
                .setValues(Map.of());
        MaterialImportBatchDO batch = new MaterialImportBatchDO().setId(99L).setMaterialTypeId(11L)
                .setSchemaVersionId(21L).setStatus("PREVIEWED").setVersion(0).setCreatedByUserId(7L)
                .setPreviewRowsJson(JsonUtils.toJsonString(List.of(
                        new MaterialImportService.PreviewRow(2, "row-a", request))));
        when(batchMapper.selectByIdForUpdate(99L, 1L)).thenReturn(batch);

        ServiceException error = assertThrows(ServiceException.class, () -> service.commit(99L, 0, 8L));

        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants
                .MATERIAL_IMPORT_BATCH_NOT_EXISTS.getCode(), error.getCode());
        verifyNoInteractions(materialService);
    }

    @Test
    void commitCreatesMaterialsForUploaderAndRecordsConfirmation() {
        MaterialSaveReqVO request = new MaterialSaveReqVO().setMaterialTypeId(11L).setTitle("素材 A")
                .setValues(Map.of());
        MaterialImportBatchDO batch = new MaterialImportBatchDO().setId(99L).setMaterialTypeId(11L)
                .setSchemaVersionId(21L).setStatus("PREVIEWED").setVersion(0).setCreatedByUserId(7L)
                .setPreviewRowsJson(JsonUtils.toJsonString(List.of(
                        new MaterialImportService.PreviewRow(2, "row-a", request))));
        when(batchMapper.selectByIdForUpdate(99L, 1L)).thenReturn(batch);
        when(batchMapper.markCommitted(org.mockito.ArgumentMatchers.eq(batch),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(1);

        service.commit(99L, 0, 7L);

        verify(materialService).createEffectiveFromImport(11L, 21L, request, 99L, 2, 7L);
        verify(batchMapper).markCommitted(org.mockito.ArgumentMatchers.eq(batch),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(7L), any());
    }

    private void stubBatchPersistence() {
        when(batchMapper.selectByIdempotencyKey(anyString())).thenReturn(null);
        when(batchMapper.insert(any(MaterialImportBatchDO.class))).thenAnswer(invocation -> {
            MaterialImportBatchDO row = invocation.getArgument(0);
            row.setId(99L);
            savedBatch.set(row);
            return 1;
        });
        when(batchMapper.selectById(99L)).thenAnswer(invocation -> savedBatch.get());
        when(errorMapper.selectByBatchId(99L)).thenReturn(List.of());
    }

    private void stubTemplate() {
        when(materialTypeService.requireType(11L)).thenReturn(type);
        when(materialTypeService.requirePublishedSchema(type)).thenReturn(schema);
    }

    private List<List<Object>> infoRows() {
        return List.of(List.of("素材类型ID", "11"), List.of("素材类型编码", "viral_content"),
                List.of("模板版本ID", "21"), List.of("模板版本号", "3"),
                List.of("模板哈希", "schema-hash"));
    }

    private List<List<String>> mainHeaders(List<MaterialFieldDefinition> fields) {
        java.util.ArrayList<List<String>> headers = new java.util.ArrayList<>(List.of(
                List.of("导入行标识[__row_key__]"), List.of("标题[__title__]"),
                List.of("封面文件ID[__cover_file_id__]"), List.of("摘要[__summary__]")));
        fields.forEach(field -> headers.add(List.of(field.getLabel() + "[" + field.getKey() + "]")));
        return headers;
    }

    private MaterialFieldDefinition field(String key, String label, String type) {
        MaterialFieldDefinition field = new MaterialFieldDefinition();
        field.setKey(key);
        field.setLabel(label);
        field.setType(type);
        return field;
    }

    private TestSheet sheet(String name, List<List<String>> head, List<? extends Collection<?>> rows) {
        return new TestSheet(name, head, rows);
    }

    private byte[] workbook(List<TestSheet> sheets) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (ExcelWriter writer = FastExcelFactory.write(output).build()) {
                int index = 0;
                for (TestSheet sheet : sheets) {
                    WriteSheet writeSheet = FastExcelFactory.writerSheet(index++, sheet.name())
                            .head(sheet.head()).build();
                    writer.write(sheet.rows(), writeSheet);
                }
            }
            return output.toByteArray();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private List<Map<Integer, String>> read(byte[] content, String sheetName) {
        return FastExcelFactory.read(new ByteArrayInputStream(content)).headRowNumber(0)
                .sheet(sheetName).doReadSync();
    }

    private record TestSheet(String name, List<List<String>> head, List<? extends Collection<?>> rows) {}
}
