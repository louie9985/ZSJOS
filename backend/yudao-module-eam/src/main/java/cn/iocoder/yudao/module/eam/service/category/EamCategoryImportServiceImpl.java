package cn.iocoder.yudao.module.eam.service.category;

import cn.hutool.core.util.StrUtil;
import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldSaveReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryImportRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategorySaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import cn.iocoder.yudao.module.eam.enums.category.EamFieldTypeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.CATEGORY_IMPORT_CONFLICT;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.CATEGORY_IMPORT_FILE_INVALID;

@Service
public class EamCategoryImportServiceImpl implements EamCategoryImportService {

    private static final Map<String, Integer> FIELD_TYPES = Map.of(
            "单行文本", EamFieldTypeEnum.TEXT.getType(),
            "多行文本", EamFieldTypeEnum.TEXTAREA.getType(),
            "数字", EamFieldTypeEnum.NUMBER.getType(),
            "日期", EamFieldTypeEnum.DATE.getType(),
            "下拉选择", EamFieldTypeEnum.SELECT.getType());

    @Resource
    private EamCategoryService categoryService;
    @Resource
    private EamCategoryFieldService fieldService;

    @Override
    public EamCategoryImportRespVO preview(byte[] content) throws IOException {
        ParsedConfig config = parse(content);
        return inspect(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EamCategoryImportRespVO commit(byte[] content) throws IOException {
        ParsedConfig config = parse(content);
        EamCategoryImportRespVO preview = inspect(config);
        if (preview.getConflictCount() > 0) {
            throw exception(CATEGORY_IMPORT_CONFLICT, preview.getConflictCount());
        }

        Map<String, EamCategoryDO> current = categoryService.getCategoryList().stream()
                .collect(Collectors.toMap(EamCategoryDO::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<CategoryRow> pending = new ArrayList<>(config.categories());
        while (!pending.isEmpty()) {
            int before = pending.size();
            pending.removeIf(row -> {
                Long parentId = StrUtil.isBlank(row.parentCode()) ? 0L
                        : current.containsKey(row.parentCode()) ? current.get(row.parentCode()).getId() : null;
                if (parentId == null) {
                    return false;
                }
                EamCategorySaveReqVO req = toCategoryReq(row, parentId);
                EamCategoryDO existing = current.get(row.code());
                if (existing == null) {
                    Long id = categoryService.createCategory(req);
                    current.put(row.code(), categoryService.getCategory(id));
                } else if (!sameCategory(existing, row, parentId)) {
                    req.setId(existing.getId());
                    categoryService.updateCategory(req);
                    current.put(row.code(), categoryService.getCategory(existing.getId()));
                }
                return true;
            });
            if (pending.size() == before) {
                throw exception(CATEGORY_IMPORT_FILE_INVALID, "存在无法解析的父分类");
            }
        }

        for (FieldRow row : config.fields()) {
            EamCategoryDO category = current.get(row.categoryCode());
            EamCategoryFieldDO existing = fieldService.getFieldListByCategoryId(category.getId()).stream()
                    .filter(field -> Objects.equals(field.getFieldKey(), row.fieldKey())).findFirst().orElse(null);
            EamCategoryFieldSaveReqVO req = toFieldReq(row, category.getId(), existing);
            if (existing == null) {
                fieldService.createField(req);
            } else if (!sameField(existing, row)) {
                req.setId(existing.getId());
                fieldService.updateField(req);
            }
        }
        return preview;
    }

    private EamCategoryImportRespVO inspect(ParsedConfig config) {
        List<EamCategoryImportRespVO.Item> items = new ArrayList<>();
        Map<String, EamCategoryDO> current = categoryService.getCategoryList().stream()
                .collect(Collectors.toMap(EamCategoryDO::getCode, Function.identity(), (a, b) -> a));
        Set<String> fileCodes = config.categories().stream().map(CategoryRow::code).collect(Collectors.toSet());
        Set<String> duplicateCodes = duplicates(config.categories(), CategoryRow::code);

        for (CategoryRow row : config.categories()) {
            String action;
            String message = null;
            if (StrUtil.hasBlank(row.code(), row.name())) {
                action = "CONFLICT";
                message = "分类编码和分类名称不能为空";
            } else if (duplicateCodes.contains(row.code())) {
                action = "CONFLICT";
                message = "分类编码在模板中重复";
            } else if (StrUtil.isNotBlank(row.parentCode())
                    && !fileCodes.contains(row.parentCode()) && !current.containsKey(row.parentCode())) {
                action = "CONFLICT";
                message = "父分类编码不存在";
            } else {
                EamCategoryDO existing = current.get(row.code());
                if (existing == null) {
                    action = "CREATE";
                } else {
                    Long parentId = StrUtil.isBlank(row.parentCode()) ? 0L
                            : current.containsKey(row.parentCode()) ? current.get(row.parentCode()).getId() : null;
                    action = parentId != null && sameCategory(existing, row, parentId) ? "SKIP" : "UPDATE";
                }
                if (StrUtil.isBlank(message) && StrUtil.isNotBlank(row.parentCode())) {
                    message = "子分类";
                }
            }
            items.add(item("CATEGORY", row.code(), row.name(), action, message));
        }

        Set<String> duplicateFields = duplicates(config.fields(), row -> row.categoryCode() + "\n" + row.fieldKey());
        for (FieldRow row : config.fields()) {
            String key = row.categoryCode() + "\n" + row.fieldKey();
            String action;
            String message = null;
            EamCategoryDO category = current.get(row.categoryCode());
            if (duplicateFields.contains(key)) {
                action = "CONFLICT";
                message = "同分类字段标识在模板中重复";
            } else if (!fileCodes.contains(row.categoryCode()) && category == null) {
                action = "CONFLICT";
                message = "所属分类编码不存在";
            } else if (!FIELD_TYPES.containsKey(row.fieldType())) {
                action = "CONFLICT";
                message = "字段类型不支持";
            } else if (StrUtil.hasBlank(row.fieldKey(), row.fieldName())) {
                action = "CONFLICT";
                message = "字段标识和字段名称不能为空";
            } else if (category == null) {
                action = "CREATE";
            } else {
                EamCategoryFieldDO existing = fieldService.getFieldListByCategoryId(category.getId()).stream()
                        .filter(field -> Objects.equals(field.getFieldKey(), row.fieldKey())).findFirst().orElse(null);
                action = existing == null ? "CREATE" : sameField(existing, row) ? "SKIP" : "UPDATE";
            }
            items.add(item("FIELD", row.fieldKey(), row.fieldName(), action, message));
        }
        return buildResult(items);
    }

    private ParsedConfig parse(byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw exception(CATEGORY_IMPORT_FILE_INVALID, "文件为空");
        }
        try {
            List<Map<Integer, String>> categoryMaps = FastExcelFactory.read(new ByteArrayInputStream(content))
                    .headRowNumber(1).sheet("分类").doReadSync();
            List<Map<Integer, String>> fieldMaps = FastExcelFactory.read(new ByteArrayInputStream(content))
                    .headRowNumber(1).sheet("字段").doReadSync();
            List<CategoryRow> categories = categoryMaps.stream().map(this::parseCategory)
                    .filter(row -> StrUtil.isNotBlank(row.code()) || StrUtil.isNotBlank(row.name())).toList();
            List<FieldRow> fields = fieldMaps.stream().map(this::parseField)
                    .filter(row -> StrUtil.isNotBlank(row.categoryCode()) || StrUtil.isNotBlank(row.fieldKey())).toList();
            if (categories.isEmpty()) {
                throw exception(CATEGORY_IMPORT_FILE_INVALID, "分类工作表没有有效数据");
            }
            return new ParsedConfig(categories, fields);
        } catch (RuntimeException ex) {
            if (ex instanceof ServiceException) {
                throw ex;
            }
            throw exception(CATEGORY_IMPORT_FILE_INVALID, "缺少分类/字段工作表或表头不匹配");
        }
    }

    private CategoryRow parseCategory(Map<Integer, String> row) {
        return new CategoryRow(cell(row, 0), cell(row, 1), cell(row, 2), parseStatus(cell(row, 3)),
                parseInteger(cell(row, 4), 0), parseMode(cell(row, 5)), StrUtil.blankToDefault(cell(row, 6), "个"), cell(row, 7));
    }

    private FieldRow parseField(Map<Integer, String> row) {
        return new FieldRow(cell(row, 0), cell(row, 1), cell(row, 2), cell(row, 3), cell(row, 4), cell(row, 5),
                parseBoolean(cell(row, 6), true), parseInteger(cell(row, 7), 0), cell(row, 8), Set.copyOf(row.keySet()));
    }

    private EamCategorySaveReqVO toCategoryReq(CategoryRow row, Long parentId) {
        EamCategorySaveReqVO req = new EamCategorySaveReqVO();
        req.setParentId(parentId); req.setCode(row.code()); req.setName(row.name()); req.setStatus(row.status());
        req.setSort(row.sort()); req.setManagementMode(row.managementMode()); req.setUnit(row.unit()); req.setRemark(row.remark());
        return req;
    }

    private EamCategoryFieldSaveReqVO toFieldReq(FieldRow row, Long categoryId, EamCategoryFieldDO existing) {
        EamCategoryFieldSaveReqVO req = new EamCategoryFieldSaveReqVO();
        req.setCategoryId(categoryId); req.setFieldKey(row.fieldKey()); req.setFieldName(row.fieldName());
        req.setFieldType(FIELD_TYPES.get(row.fieldType()));
        if (existing == null) {
            req.setRequired(false); req.setAdminVisible(row.adminVisible()); req.setCollectionVisible(true);
            req.setCollectionRequired(false); req.setSort(row.sort());
            req.setOptionSource(StrUtil.emptyToNull(row.optionSource()));
            req.setDictType(StrUtil.emptyToNull(row.dictType()));
        } else {
            req.setRequired(existing.getRequired()); req.setOptions(existing.getOptions());
            req.setAdminVisible(row.provided(6) ? row.adminVisible() : existing.getAdminVisible());
            req.setCollectionVisible(existing.getCollectionVisible());
            req.setCollectionRequired(existing.getCollectionRequired());
            req.setConditionRule(existing.getConditionRule());
            req.setSort(row.provided(7) ? row.sort() : existing.getSort());
            req.setOptionSource(row.provided(4) ? StrUtil.emptyToNull(row.optionSource()) : existing.getOptionSource());
            req.setDictType(row.provided(5) ? StrUtil.emptyToNull(row.dictType()) : existing.getDictType());
        }
        return req;
    }

    private boolean sameCategory(EamCategoryDO existing, CategoryRow row, Long parentId) {
        return Objects.equals(existing.getParentId(), parentId) && Objects.equals(existing.getName(), row.name())
                && Objects.equals(existing.getStatus(), row.status()) && Objects.equals(existing.getSort(), row.sort())
                && Objects.equals(existing.getManagementMode(), row.managementMode()) && Objects.equals(existing.getUnit(), row.unit())
                && Objects.equals(StrUtil.nullToEmpty(existing.getRemark()), StrUtil.nullToEmpty(row.remark()));
    }

    private boolean sameField(EamCategoryFieldDO existing, FieldRow row) {
        return Objects.equals(existing.getFieldName(), row.fieldName())
                && Objects.equals(existing.getFieldType(), FIELD_TYPES.get(row.fieldType()))
                && (!row.provided(4) || Objects.equals(existing.getOptionSource(), StrUtil.emptyToNull(row.optionSource())))
                && (!row.provided(5) || Objects.equals(existing.getDictType(), StrUtil.emptyToNull(row.dictType())))
                && (!row.provided(6) || Objects.equals(Boolean.TRUE.equals(existing.getAdminVisible()), row.adminVisible()))
                && (!row.provided(7) || Objects.equals(existing.getSort(), row.sort()));
    }


    private static <T> Set<String> duplicates(List<T> rows, Function<T, String> keyFunction) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        rows.forEach(row -> { String key = keyFunction.apply(row); if (!seen.add(key)) duplicates.add(key); });
        return duplicates;
    }

    private EamCategoryImportRespVO buildResult(List<EamCategoryImportRespVO.Item> items) {
        Map<String, Integer> counts = new HashMap<>();
        items.forEach(item -> counts.merge(item.getAction(), 1, Integer::sum));
        int categoryCount = (int) items.stream().filter(item -> "CATEGORY".equals(item.getKind())).count();
        int leafCategoryCount = (int) items.stream().filter(item -> "CATEGORY".equals(item.getKind())
                && StrUtil.isNotBlank(item.getMessage()) && item.getMessage().contains("子分类")).count();
        int fieldCount = (int) items.stream().filter(item -> "FIELD".equals(item.getKind())).count();
        int legacyFieldCount = (int) items.stream().filter(item -> "FIELD".equals(item.getKind())
                && isLegacyField(item)).count();
        int credentialFieldCount = (int) items.stream().filter(item -> "FIELD".equals(item.getKind())
                && isCredentialField(item)).count();
        return EamCategoryImportRespVO.builder().items(items)
                .createCount(counts.getOrDefault("CREATE", 0)).updateCount(counts.getOrDefault("UPDATE", 0))
                .skipCount(counts.getOrDefault("SKIP", 0)).conflictCount(counts.getOrDefault("CONFLICT", 0))
                .categoryCount(categoryCount).leafCategoryCount(leafCategoryCount).fieldCount(fieldCount)
                .legacyFieldCount(legacyFieldCount).credentialFieldCount(credentialFieldCount)
                .allManagementFieldsOptional(true).build();
    }

    private static boolean isLegacyField(EamCategoryImportRespVO.Item item) {
        String value = (StrUtil.nullToEmpty(item.getCode()) + " " + StrUtil.nullToEmpty(item.getName())).toLowerCase();
        return value.contains("source_") || value.contains("原表") || value.contains("原始") || value.contains("来源字段");
    }

    private static boolean isCredentialField(EamCategoryImportRespVO.Item item) {
        String value = (StrUtil.nullToEmpty(item.getCode()) + " " + StrUtil.nullToEmpty(item.getName())).toLowerCase();
        return value.contains("password") || value.contains("密码") || value.contains("凭据");
    }

    private static EamCategoryImportRespVO.Item item(String kind, String code, String name, String action, String message) {
        return EamCategoryImportRespVO.Item.builder().kind(kind).code(code).name(name).action(action).message(message).build();
    }

    private static String cell(Map<Integer, String> row, int index) { return StrUtil.trim(row.get(index)); }
    private static int parseInteger(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private static int parseStatus(String value) { return "关闭".equals(value) || "1".equals(value) ? 1 : 0; }
    private static int parseMode(String value) { return "批量".equals(value) || "2".equals(value) ? EamManagementModeEnum.BATCH.getMode() : EamManagementModeEnum.SERIALIZED.getMode(); }
    private static boolean parseBoolean(String value, boolean fallback) { return StrUtil.isBlank(value) ? fallback : Set.of("是", "true", "1").contains(value.toLowerCase()); }

    private record CategoryRow(String code, String name, String parentCode, Integer status, Integer sort,
                               Integer managementMode, String unit, String remark) {}
    private record FieldRow(String categoryCode, String fieldKey, String fieldName, String fieldType,
                             String optionSource, String dictType, Boolean adminVisible, Integer sort, String remark,
                             Set<Integer> providedColumns) {
        private boolean provided(int index) { return providedColumns.contains(index); }
    }
    private record ParsedConfig(List<CategoryRow> categories, List<FieldRow> fields) {}

}
