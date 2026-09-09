package cn.iocoder.yudao.module.zsjos.service.material;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.write.metadata.WriteSheet;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo.MaterialImportErrorRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo.MaterialImportPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo.MaterialImportRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialImportBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialImportErrorDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialImportBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialImportErrorMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MaterialImportService {

    private static final String MAIN_SHEET = "素材";
    private static final String INFO_SHEET = "模板信息";
    private static final String GUIDE_SHEET = "填写说明";
    private static final String ROW_KEY = "__row_key__";
    private static final String TITLE_KEY = "__title__";
    private static final String COVER_KEY = "__cover_file_id__";
    private static final String SUMMARY_KEY = "__summary__";
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_GROUP_ROWS = 100_000;

    @Resource private MaterialTypeService materialTypeService;
    @Resource private MaterialSchemaService schemaService;
    @Resource private MaterialService materialService;
    @Resource private MaterialImportBatchMapper batchMapper;
    @Resource private MaterialImportErrorMapper errorMapper;

    public WorkbookFile buildTemplate(Long materialTypeId) {
        TemplateContext context = requireTemplate(materialTypeId);
        List<WorkbookSheet> sheets = new ArrayList<>();
        sheets.add(new WorkbookSheet(INFO_SHEET, headers("配置项", "值"), List.of(
                List.of("素材类型ID", String.valueOf(context.type().getId())),
                List.of("素材类型编码", context.type().getCode()),
                List.of("模板版本ID", String.valueOf(context.schema().getId())),
                List.of("模板版本号", String.valueOf(context.schema().getVersionNo())),
                List.of("模板哈希", context.schema().getSchemaHash()))));

        List<List<String>> mainHead = new ArrayList<>();
        mainHead.add(List.of(header("导入行标识", ROW_KEY)));
        mainHead.add(List.of(header("标题", TITLE_KEY)));
        mainHead.add(List.of(header("封面文件ID", COVER_KEY)));
        mainHead.add(List.of(header("摘要", SUMMARY_KEY)));
        for (MaterialFieldDefinition field : context.fields()) {
            if (!FIELD_REPEAT_GROUP.equals(field.getType())) {
                mainHead.add(List.of(header(field.getLabel(), field.getKey())));
            }
        }
        sheets.add(new WorkbookSheet(MAIN_SHEET, mainHead, List.of()));

        List<List<Object>> guideRows = new ArrayList<>();
        guideRows.add(List.of(ROW_KEY, "导入行标识", "TEXT", "是", "主表与重复组子表的关联键，同一主表内不可重复"));
        guideRows.add(List.of(TITLE_KEY, "标题", "TEXT", "是", "最多 255 个字符"));
        guideRows.add(List.of(COVER_KEY, "封面文件ID", "IMAGE", "否", "填写已上传到素材目录的文件 ID"));
        guideRows.add(List.of(SUMMARY_KEY, "摘要", "TEXTAREA", "否", "最多 2000 个字符"));
        for (MaterialFieldDefinition field : context.fields()) {
            appendGuide(guideRows, "", field);
            if (FIELD_REPEAT_GROUP.equals(field.getType())) {
                List<List<String>> groupHead = new ArrayList<>();
                groupHead.add(List.of(header("导入行标识", ROW_KEY)));
                for (MaterialFieldDefinition child : field.getChildren()) {
                    groupHead.add(List.of(header(child.getLabel(), field.getKey() + "." + child.getKey())));
                }
                sheets.add(new WorkbookSheet(groupSheet(field), groupHead, List.of()));
            }
        }
        sheets.add(new WorkbookSheet(GUIDE_SHEET,
                headers("字段编码", "字段名称", "组件类型", "必填", "填写规则"), guideRows));
        return new WorkbookFile(context.type().getName() + "-导入模板.xlsx", writeWorkbook(sheets));
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialImportRespVO preview(Long materialTypeId, String sourceFileName, byte[] content,
                                        String idempotencyKey, Long userId) {
        if (content == null || content.length == 0) throw exception(MATERIAL_IMPORT_INVALID, "导入文件为空");
        String sourceHash = DigestUtil.sha256Hex(content);
        MaterialImportBatchDO replay = batchMapper.selectByIdempotencyKey(idempotencyKey);
        if (replay != null) {
            if (!Objects.equals(replay.getMaterialTypeId(), materialTypeId)
                    || !Objects.equals(replay.getSourceFileHash(), sourceHash)) {
                throw exception(MATERIAL_IMPORT_STATE_INVALID);
            }
            return get(replay.getId(), userId);
        }

        TemplateContext context = requireTemplate(materialTypeId);
        ParsedWorkbook parsed = parseWorkbook(content, context);
        List<PreviewRow> validRows = new ArrayList<>();
        List<PendingError> errors = new ArrayList<>(parsed.errors());
        for (ParsedRow row : parsed.rows()) {
            try {
                materialService.validateImport(context.type().getId(), context.schema().getId(), row.request(), userId);
                validRows.add(new PreviewRow(row.rowNo(), row.rowKey(), row.request()));
            } catch (ServiceException error) {
                errors.add(new PendingError(MAIN_SHEET, row.rowNo(), null, "VALIDATION",
                        safeMessage(error.getMessage()), JsonUtils.toJsonString(row.request())));
            }
        }

        MaterialImportBatchDO batch = new MaterialImportBatchDO();
        batch.setBatchNo(nextBatchNo());
        batch.setMaterialTypeId(context.type().getId());
        batch.setSchemaVersionId(context.schema().getId());
        batch.setSourceFileName(safeFileName(sourceFileName));
        batch.setSourceFileHash(sourceHash);
        batch.setStatus(IMPORT_PREVIEWED);
        batch.setTotalCount(parsed.totalCount());
        batch.setSuccessCount(validRows.size());
        batch.setFailureCount(parsed.totalCount() - validRows.size());
        batch.setPreviewRowsJson(JsonUtils.toJsonString(validRows));
        batch.setIdempotencyKey(idempotencyKey);
        batch.setCreatedByUserId(userId);
        batch.setVersion(0);
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException duplicate) {
            MaterialImportBatchDO concurrent = batchMapper.selectByIdempotencyKey(idempotencyKey);
            if (concurrent != null && Objects.equals(concurrent.getMaterialTypeId(), materialTypeId)
                    && Objects.equals(concurrent.getSourceFileHash(), sourceHash)) {
                return get(concurrent.getId(), userId);
            }
            throw exception(MATERIAL_IMPORT_STATE_INVALID);
        }
        insertErrors(batch.getId(), errors);
        return get(batch.getId(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void commit(Long batchId, Integer expectedVersion, Long userId) {
        MaterialImportBatchDO batch = batchMapper.selectByIdForUpdate(batchId,
                TenantContextHolder.getRequiredTenantId());
        if (batch == null) throw exception(MATERIAL_IMPORT_BATCH_NOT_EXISTS);
        requireOwner(batch, userId);
        if (IMPORT_COMMITTED.equals(batch.getStatus())) return;
        if (!IMPORT_PREVIEWED.equals(batch.getStatus()) || !Objects.equals(batch.getVersion(), expectedVersion)) {
            throw exception(MATERIAL_IMPORT_VERSION_CONFLICT);
        }
        List<PreviewRow> rows;
        try {
            rows = JsonUtils.parseArray(batch.getPreviewRowsJson(), PreviewRow.class);
        } catch (RuntimeException error) {
            throw exception(MATERIAL_IMPORT_STATE_INVALID);
        }
        for (PreviewRow row : rows) {
            materialService.createEffectiveFromImport(batch.getMaterialTypeId(), batch.getSchemaVersionId(),
                    row.request(), batch.getId(), row.rowNo(), batch.getCreatedByUserId());
        }
        if (batchMapper.markCommitted(batch, expectedVersion, userId, LocalDateTime.now()) != 1) {
            throw exception(MATERIAL_IMPORT_VERSION_CONFLICT);
        }
    }

    public PageResult<MaterialImportRespVO> getPage(MaterialImportPageReqVO request, Long userId) {
        PageResult<MaterialImportBatchDO> page = batchMapper.selectPage(request, userId);
        return new PageResult<>(page.getList().stream().map(row -> toResponse(row, false)).toList(), page.getTotal());
    }

    public MaterialImportRespVO get(Long id, Long userId) {
        MaterialImportBatchDO batch = batchMapper.selectById(id);
        if (batch == null) throw exception(MATERIAL_IMPORT_BATCH_NOT_EXISTS);
        requireOwner(batch, userId);
        return toResponse(batch, true);
    }

    public WorkbookFile buildErrorReport(Long id, Long userId) {
        MaterialImportRespVO batch = get(id, userId);
        List<List<Object>> rows = batch.getErrors().stream().map(error -> List.<Object>of(
                error.getSheetName(), error.getRowNo(), Objects.toString(error.getFieldKey(), ""),
                error.getErrorCode(), error.getErrorMessage(), Objects.toString(error.getRowSnapshotJson(), "")))
                .toList();
        return new WorkbookFile(batch.getBatchNo() + "-错误报告.xlsx", writeWorkbook(List.of(
                new WorkbookSheet("错误明细", headers("工作表", "行号", "字段编码", "错误编码", "错误说明",
                        "原始行快照"), rows))));
    }

    private ParsedWorkbook parseWorkbook(byte[] content, TemplateContext context) {
        try {
            validateWorkbookInfo(readSheet(content, INFO_SHEET), context);
            List<Map<Integer, String>> mainRows = readSheet(content, MAIN_SHEET);
            if (mainRows.isEmpty()) throw exception(MATERIAL_IMPORT_INVALID, "素材工作表缺少表头");
            Map<String, Integer> mainHeaders = headers(mainRows.getFirst());
            validateHeaders(mainHeaders, expectedMainHeaders(context.fields()), MAIN_SHEET);

            Map<String, Map<String, List<GroupRow>>> groups = new HashMap<>();
            Set<String> invalidGroupRowKeys = new HashSet<>();
            List<PendingError> errors = new ArrayList<>();
            int groupRowCount = 0;
            for (MaterialFieldDefinition group : context.fields()) {
                if (!FIELD_REPEAT_GROUP.equals(group.getType())) continue;
                ParsedGroup parsedGroup = parseGroupSheet(content, group, errors,
                        MAX_GROUP_ROWS - groupRowCount);
                groupRowCount += parsedGroup.rowCount();
                groups.put(group.getKey(), parsedGroup.rows());
                invalidGroupRowKeys.addAll(parsedGroup.invalidRowKeys());
            }

            List<ParsedRow> rows = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();
            Set<String> duplicateKeys = new HashSet<>();
            int totalCount = 0;
            for (int index = 1; index < mainRows.size(); index++) {
                Map<Integer, String> raw = mainRows.get(index);
                if (blankRow(raw)) continue;
                totalCount++;
                if (totalCount > MAX_ROWS) {
                    throw exception(MATERIAL_IMPORT_INVALID, "单次导入不能超过 " + MAX_ROWS + " 条");
                }
                int rowNo = index + 1;
                String rowKey = cell(raw, mainHeaders, header("导入行标识", ROW_KEY));
                if (StrUtil.isBlank(rowKey)) {
                    errors.add(error(MAIN_SHEET, rowNo, ROW_KEY, "ROW_KEY_REQUIRED", "导入行标识不能为空", raw));
                    continue;
                }
                if (!seenKeys.add(rowKey)) duplicateKeys.add(rowKey);
                try {
                    MaterialSaveReqVO request = parseMainRow(raw, mainHeaders, context, groups, rowKey);
                    rows.add(new ParsedRow(rowNo, rowKey, request));
                } catch (RuntimeException error) {
                    errors.add(error(MAIN_SHEET, rowNo, null, "CELL_FORMAT", safeMessage(error.getMessage()), raw));
                }
            }
            if (totalCount == 0) throw exception(MATERIAL_IMPORT_INVALID, "素材工作表没有数据行");
            if (!duplicateKeys.isEmpty()) {
                rows.stream().filter(row -> duplicateKeys.contains(row.rowKey())).forEach(row ->
                        errors.add(new PendingError(MAIN_SHEET, row.rowNo(), ROW_KEY, "ROW_KEY_DUPLICATE",
                                "导入行标识重复：" + row.rowKey(), JsonUtils.toJsonString(row.request()))));
                rows.removeIf(row -> duplicateKeys.contains(row.rowKey()));
            }
            rows.stream().filter(row -> invalidGroupRowKeys.contains(row.rowKey())).forEach(row ->
                    errors.add(new PendingError(MAIN_SHEET, row.rowNo(), ROW_KEY, "GROUP_ROW_INVALID",
                            "重复字段组包含格式错误的数据行：" + row.rowKey(),
                            JsonUtils.toJsonString(row.request()))));
            rows.removeIf(row -> invalidGroupRowKeys.contains(row.rowKey()));
            groups.forEach((groupKey, byRow) -> byRow.forEach((rowKey, childRows) -> {
                if (!seenKeys.contains(rowKey)) childRows.forEach(child -> errors.add(new PendingError(
                        groupSheetName(groupKey), child.rowNo(), ROW_KEY, "ORPHAN_GROUP_ROW",
                        "重复组引用的导入行标识不存在：" + rowKey, JsonUtils.toJsonString(child.values()))));
            }));
            return new ParsedWorkbook(rows, errors, totalCount);
        } catch (ServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            throw exception(MATERIAL_IMPORT_INVALID, "文件无法读取或模板结构不匹配");
        }
    }

    private MaterialSaveReqVO parseMainRow(Map<Integer, String> raw, Map<String, Integer> headers,
                                           TemplateContext context,
                                           Map<String, Map<String, List<GroupRow>>> groups, String rowKey) {
        MaterialSaveReqVO request = new MaterialSaveReqVO();
        request.setMaterialTypeId(context.type().getId());
        request.setTitle(cell(raw, headers, header("标题", TITLE_KEY)));
        request.setSummary(emptyToNull(cell(raw, headers, header("摘要", SUMMARY_KEY))));
        request.setCoverFileId(parseOptionalLong(cell(raw, headers, header("封面文件ID", COVER_KEY))));
        Map<String, Object> values = new LinkedHashMap<>();
        for (MaterialFieldDefinition field : context.fields()) {
            if (FIELD_REPEAT_GROUP.equals(field.getType())) {
                List<GroupRow> groupRows = groups.getOrDefault(field.getKey(), Map.of())
                        .getOrDefault(rowKey, List.of());
                if (!groupRows.isEmpty()) values.put(field.getKey(), groupRows.stream().map(GroupRow::values).toList());
            } else {
                String value = cell(raw, headers, header(field.getLabel(), field.getKey()));
                Object parsed = parseCell(field, value);
                if (parsed != null) values.put(field.getKey(), parsed);
            }
        }
        request.setValues(values);
        return request;
    }

    private ParsedGroup parseGroupSheet(byte[] content, MaterialFieldDefinition group,
                                        List<PendingError> errors, int remainingRows) {
        String sheetName = groupSheet(group);
        List<Map<Integer, String>> rows = readSheet(content, sheetName);
        if (rows.isEmpty()) throw exception(MATERIAL_IMPORT_INVALID, sheetName + " 工作表缺少表头");
        Map<String, Integer> headers = headers(rows.getFirst());
        Set<String> expected = new LinkedHashSet<>();
        expected.add(header("导入行标识", ROW_KEY));
        group.getChildren().forEach(child -> expected.add(header(child.getLabel(),
                group.getKey() + "." + child.getKey())));
        validateHeaders(headers, expected, sheetName);
        Map<String, List<GroupRow>> result = new LinkedHashMap<>();
        Set<String> invalidRowKeys = new HashSet<>();
        int rowCount = 0;
        for (int index = 1; index < rows.size(); index++) {
            Map<Integer, String> raw = rows.get(index);
            if (blankRow(raw)) continue;
            rowCount++;
            if (rowCount > remainingRows) {
                throw exception(MATERIAL_IMPORT_INVALID,
                        "重复字段组数据行合计不能超过 " + MAX_GROUP_ROWS + " 条");
            }
            int rowNo = index + 1;
            String rowKey = cell(raw, headers, header("导入行标识", ROW_KEY));
            if (StrUtil.isBlank(rowKey)) {
                errors.add(error(sheetName, rowNo, ROW_KEY, "ROW_KEY_REQUIRED", "导入行标识不能为空", raw));
                continue;
            }
            try {
                Map<String, Object> values = new LinkedHashMap<>();
                for (MaterialFieldDefinition child : group.getChildren()) {
                    Object parsed = parseCell(child, cell(raw, headers,
                            header(child.getLabel(), group.getKey() + "." + child.getKey())));
                    if (parsed != null) values.put(child.getKey(), parsed);
                }
                result.computeIfAbsent(rowKey, ignored -> new ArrayList<>()).add(new GroupRow(rowNo, values));
            } catch (RuntimeException error) {
                errors.add(error(sheetName, rowNo, null, "CELL_FORMAT", safeMessage(error.getMessage()), raw));
                invalidRowKeys.add(rowKey);
            }
        }
        return new ParsedGroup(result, invalidRowKeys, rowCount);
    }

    private Object parseCell(MaterialFieldDefinition field, String value) {
        if (StrUtil.isBlank(value)) return null;
        String normalized = value.trim();
        return switch (field.getType()) {
            case FIELD_NUMBER -> new BigDecimal(normalized);
            case FIELD_DICT_MULTI -> split(normalized);
            case FIELD_EMPLOYEE, FIELD_DEPARTMENT -> Boolean.TRUE.equals(field.getMultiple())
                    ? split(normalized).stream().map(Long::valueOf).toList() : Long.valueOf(normalized);
            case FIELD_IMAGE, FIELD_VIDEO, FIELD_ATTACHMENT -> split(normalized).stream().map(Long::valueOf).toList();
            case FIELD_DATE -> parseDate(normalized).toString();
            case FIELD_DATETIME -> parseDateTime(normalized).toString();
            default -> normalized;
        };
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return parseDateTime(value).toLocalDate();
        }
    }

    private LocalDateTime parseDateTime(String value) {
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next documented Excel date-time format.
            }
        }
        throw new DateTimeParseException("日期时间格式必须为 yyyy-MM-dd HH:mm:ss", value, 0);
    }

    private List<String> split(String value) {
        return java.util.Arrays.stream(value.split("[|,，;；\\n]"))
                .map(String::trim).filter(StrUtil::isNotBlank).distinct().toList();
    }

    private void requireOwner(MaterialImportBatchDO batch, Long userId) {
        if (!Objects.equals(batch.getCreatedByUserId(), userId)) {
            throw exception(MATERIAL_IMPORT_BATCH_NOT_EXISTS);
        }
    }

    private TemplateContext requireTemplate(Long materialTypeId) {
        MaterialTypeDO type = materialTypeService.requireType(materialTypeId);
        if (!Boolean.TRUE.equals(type.getAllowImport())
                || !cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())) {
            throw exception(MATERIAL_IMPORT_INVALID, "该素材类型未启用导入");
        }
        MaterialSchemaVersionDO schema = materialTypeService.requirePublishedSchema(type);
        return new TemplateContext(type, schema, schemaService.parseFields(schema.getFieldsJson()));
    }

    private void validateWorkbookInfo(List<Map<Integer, String>> rows, TemplateContext context) {
        if (rows.size() < 2) throw exception(MATERIAL_IMPORT_INVALID, "模板信息缺失");
        Map<String, String> info = new LinkedHashMap<>();
        for (int index = 1; index < rows.size(); index++) {
            info.put(StrUtil.trim(rows.get(index).get(0)), StrUtil.trim(rows.get(index).get(1)));
        }
        if (!Objects.equals(info.get("素材类型ID"), String.valueOf(context.type().getId()))
                || !Objects.equals(info.get("模板版本ID"), String.valueOf(context.schema().getId()))
                || !Objects.equals(info.get("模板哈希"), context.schema().getSchemaHash())) {
            throw exception(MATERIAL_IMPORT_INVALID, "导入文件不是当前素材类型的最新模板");
        }
    }

    private Set<String> expectedMainHeaders(List<MaterialFieldDefinition> fields) {
        Set<String> result = new LinkedHashSet<>(List.of(header("导入行标识", ROW_KEY),
                header("标题", TITLE_KEY), header("封面文件ID", COVER_KEY), header("摘要", SUMMARY_KEY)));
        fields.stream().filter(field -> !FIELD_REPEAT_GROUP.equals(field.getType()))
                .forEach(field -> result.add(header(field.getLabel(), field.getKey())));
        return result;
    }

    private void validateHeaders(Map<String, Integer> actual, Set<String> expected, String sheetName) {
        if (!actual.keySet().equals(expected)) {
            throw exception(MATERIAL_IMPORT_INVALID, sheetName + " 工作表表头与模板不一致");
        }
    }

    private List<Map<Integer, String>> readSheet(byte[] content, String sheetName) {
        try {
            return FastExcelFactory.read(new ByteArrayInputStream(content)).headRowNumber(0)
                    .sheet(sheetName).doReadSync();
        } catch (RuntimeException error) {
            throw exception(MATERIAL_IMPORT_INVALID, "缺少工作表：" + sheetName);
        }
    }

    private MaterialImportRespVO toResponse(MaterialImportBatchDO batch, boolean includeErrors) {
        MaterialImportRespVO response = BeanUtils.toBean(batch, MaterialImportRespVO.class);
        response.setMaterialTypeName(materialTypeService.requireType(batch.getMaterialTypeId()).getName());
        response.setErrors(includeErrors ? errorMapper.selectByBatchId(batch.getId()).stream()
                .map(row -> BeanUtils.toBean(row, MaterialImportErrorRespVO.class)).toList() : List.of());
        return response;
    }

    private void insertErrors(Long batchId, List<PendingError> errors) {
        if (errors.isEmpty()) return;
        errorMapper.insertBatch(errors.stream().map(error -> {
            MaterialImportErrorDO row = new MaterialImportErrorDO();
            row.setBatchId(batchId);
            row.setSheetName(error.sheetName());
            row.setRowNo(error.rowNo());
            row.setFieldKey(error.fieldKey());
            row.setErrorCode(error.errorCode());
            row.setErrorMessage(error.message());
            row.setRowSnapshotJson(error.snapshotJson());
            return row;
        }).toList());
    }

    private byte[] writeWorkbook(List<WorkbookSheet> sheets) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (ExcelWriter writer = FastExcelFactory.write(output).build()) {
                int index = 0;
                for (WorkbookSheet sheet : sheets) {
                    WriteSheet writeSheet = FastExcelFactory.writerSheet(index++, sheet.name())
                            .head(sheet.head()).build();
                    writer.write(sheet.rows(), writeSheet);
                }
            }
            return output.toByteArray();
        } catch (Exception error) {
            throw exception(MATERIAL_IMPORT_INVALID, "生成 Excel 文件失败");
        }
    }

    private void appendGuide(List<List<Object>> rows, String parent, MaterialFieldDefinition field) {
        String key = parent.isEmpty() ? field.getKey() : parent + "." + field.getKey();
        List<String> rules = new ArrayList<>();
        if (field.getDictType() != null) rules.add("字典：" + field.getDictType() + "，填写字典值");
        if (FIELD_DATE.equals(field.getType())) rules.add("格式：yyyy-MM-dd");
        if (FIELD_DATETIME.equals(field.getType())) rules.add("格式：yyyy-MM-dd HH:mm:ss");
        if (Boolean.TRUE.equals(field.getMultiple()) || FIELD_DICT_MULTI.equals(field.getType())
                || FILE_FIELD_TYPES.contains(field.getType())) rules.add("多个值使用逗号分隔");
        if (field.getMaxLength() != null) rules.add("最长 " + field.getMaxLength() + " 字符");
        if (field.getMinCount() != null || field.getMaxCount() != null) {
            rules.add("数量 " + Objects.toString(field.getMinCount(), "0") + "-"
                    + Objects.toString(field.getMaxCount(), "默认上限"));
        }
        if (FILE_FIELD_TYPES.contains(field.getType())) rules.add("填写已有素材文件 ID");
        rows.add(List.of(key, field.getLabel(), field.getType(),
                Boolean.TRUE.equals(field.getRequired()) ? "是" : "否", String.join("；", rules)));
        if (FIELD_REPEAT_GROUP.equals(field.getType())) {
            field.getChildren().forEach(child -> appendGuide(rows, key, child));
        }
    }

    private String groupSheet(MaterialFieldDefinition group) {
        return groupSheetName(group.getKey());
    }

    private String groupSheetName(String key) {
        String prefix = key.length() <= 20 ? key : key.substring(0, 20);
        return "组-" + prefix + "-" + DigestUtil.sha256Hex(key).substring(0, 6);
    }

    private static List<List<String>> headers(String... names) {
        return java.util.Arrays.stream(names).map(List::of).toList();
    }

    private String header(String label, String key) {
        return label + "[" + key + "]";
    }

    private Map<String, Integer> headers(Map<Integer, String> row) {
        Map<String, Integer> result = new LinkedHashMap<>();
        row.forEach((index, value) -> {
            String name = StrUtil.trim(value);
            if (StrUtil.isNotBlank(name) && result.put(name, index) != null) {
                throw exception(MATERIAL_IMPORT_INVALID, "表头重复：" + name);
            }
        });
        return result;
    }

    private String cell(Map<Integer, String> row, Map<String, Integer> headers, String name) {
        Integer index = headers.get(name);
        return index == null ? "" : StrUtil.trim(row.get(index));
    }

    private boolean blankRow(Map<Integer, String> row) {
        return row == null || row.values().stream().allMatch(StrUtil::isBlank);
    }

    private PendingError error(String sheet, int rowNo, String fieldKey, String code,
                               String message, Object snapshot) {
        return new PendingError(sheet, rowNo, fieldKey, code, safeMessage(message), JsonUtils.toJsonString(snapshot));
    }

    private Long parseOptionalLong(String value) {
        return StrUtil.isBlank(value) ? null : Long.valueOf(value);
    }

    private String emptyToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String safeFileName(String value) {
        String name = StrUtil.blankToDefault(value, "material-import.xlsx").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private String safeMessage(String value) {
        String message = StrUtil.blankToDefault(value, "数据格式无效");
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String nextBatchNo() {
        return "MI-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public record WorkbookFile(String fileName, byte[] content) {}
    private record WorkbookSheet(String name, List<List<String>> head, List<? extends Collection<?>> rows) {}
    private record TemplateContext(MaterialTypeDO type, MaterialSchemaVersionDO schema,
                                   List<MaterialFieldDefinition> fields) {}
    private record ParsedWorkbook(List<ParsedRow> rows, List<PendingError> errors, int totalCount) {}
    private record ParsedRow(int rowNo, String rowKey, MaterialSaveReqVO request) {}
    private record ParsedGroup(Map<String, List<GroupRow>> rows, Set<String> invalidRowKeys, int rowCount) {}
    private record GroupRow(int rowNo, Map<String, Object> values) {}
    private record PendingError(String sheetName, int rowNo, String fieldKey, String errorCode,
                                String message, String snapshotJson) {}
    public record PreviewRow(int rowNo, String rowKey, MaterialSaveReqVO request) {}
}
