package cn.iocoder.yudao.module.eam.service.asset;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportPreviewRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetImportBatchDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetImportRowDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetImportBatchMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetImportRowMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService.NormalizedExtFields;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.coderule.EamCodeRuleService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_IMPORT_HAS_ERRORS;

@Service
public class EamAssetLedgerImportServiceImpl implements EamAssetLedgerImportService {

    @Resource
    private EamAssetLedgerParser parser;
    @Resource
    private EamCategoryService categoryService;
    @Resource
    private EamCategoryFieldService categoryFieldService;
    @Resource
    private EamAssetMapper assetMapper;
    @Resource
    private EamAssetImportBatchMapper batchMapper;
    @Resource
    private EamAssetImportRowMapper importRowMapper;
    @Resource
    private EamCodeRuleService codeRuleService;
    @Resource
    private EamAssetChangeLogService changeLogService;
    @Resource
    private HrmEmployeeApi employeeApi;
    @Resource
    private DictDataApi dictDataApi;

    @Override
    public EamAssetImportPreviewRespVO preview(byte[] content, String fileName, boolean updateExisting) {
        return prepare(content, updateExisting).response();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EamAssetImportPreviewRespVO commit(byte[] content, String fileName, boolean updateExisting) {
        PreparedImport prepared = prepare(content, updateExisting);
        EamAssetImportPreviewRespVO response = prepared.response();
        if (response.getErrorCount() != null && response.getErrorCount() > 0) {
            throw exception(ASSET_IMPORT_HAS_ERRORS, response.getErrorCount());
        }
        EamAssetImportBatchDO batch = new EamAssetImportBatchDO();
        batch.setFileHash(response.getFileHash());
        batch.setFileName(StrUtil.blankToDefault(fileName, "资产台账.xlsx"));
        batch.setSheetName(EamAssetLedgerParser.SHEET_NAME);
        batch.setTotalRows(response.getTotalRows());
        batch.setCreateCount(response.getCreateCount());
        batch.setUpdateCount(response.getUpdateCount());
        batch.setSkipCount(response.getSkipCount());
        batch.setWarningCount(response.getWarningCount());
        batch.setOperatorId(SecurityFrameworkUtils.getLoginUserId());
        batchMapper.insert(batch);

        for (PreparedRow item : prepared.rows()) {
            EamAssetImportPreviewRespVO.Row previewRow = item.previewRow();
            if ("SKIP_SAME_FILE".equals(previewRow.getAction())) {
                continue;
            }
            EamAssetDO asset;
            int importAction;
            if (item.existingAsset() != null && !updateExisting) {
                asset = item.existingAsset();
                importAction = 3;
            } else if (item.existingAsset() != null) {
                EamAssetDO before = item.existingAsset();
                asset = buildAsset(item);
                asset.setId(before.getId());
                asset.setAssetCode(before.getAssetCode());
                assetMapper.updateById(asset);
                asset = assetMapper.selectById(before.getId());
                changeLogService.record(before, asset, EamChangeTypeEnum.EDIT.getType(), batch.getId(), "台账导入更新");
                importAction = 2;
            } else {
                asset = buildAsset(item);
                if (StrUtil.isBlank(asset.getAssetCode())) {
                    asset.setAssetCode(codeRuleService.generateAssetCode(asset.getCategoryId()));
                } else if (assetMapper.selectByAssetCode(asset.getAssetCode()) != null) {
                    throw exception(ASSET_CODE_DUPLICATE);
                }
                assetMapper.insert(asset);
                changeLogService.record(null, asset, EamChangeTypeEnum.CREATE.getType(), batch.getId(), "台账导入建档");
                importAction = 1;
            }
            previewRow.setAssetCode(asset.getAssetCode());
            EamAssetImportRowDO source = new EamAssetImportRowDO();
            source.setBatchId(batch.getId());
            source.setFileHash(response.getFileHash());
            source.setSheetName(EamAssetLedgerParser.SHEET_NAME);
            source.setRowNum(item.source().rowNum());
            source.setAssetId(asset.getId());
            source.setAssetCode(asset.getAssetCode());
            source.setImportAction(importAction);
            importRowMapper.insert(source);
            writeHistory(item, asset, batch.getId());
        }
        response.setBatchId(batch.getId());
        return response;
    }

    private PreparedImport prepare(byte[] content, boolean updateExisting) {
        String fileHash = DigestUtil.sha256Hex(content);
        List<EamAssetLedgerParser.LedgerRow> sourceRows = parser.parse(content);
        Map<Integer, EamAssetImportRowDO> importedRows = importRowMapper
                .selectMapByFile(fileHash, EamAssetLedgerParser.SHEET_NAME);
        Map<String, EamAssetDO> assetsByCode = assetMapper.selectListByAssetCodes(sourceRows.stream()
                        .map(EamAssetLedgerParser.LedgerRow::assetCode).filter(StrUtil::isNotBlank).toList()).stream()
                .collect(Collectors.toMap(EamAssetDO::getAssetCode, Function.identity(), (a, b) -> a));

        Map<String, EamCategoryDO> categories = categoryService.getCategoryList().stream()
                .filter(category -> StrUtil.isNotBlank(category.getCode()))
                .collect(Collectors.toMap(EamCategoryDO::getCode, Function.identity(), (a, b) -> a));

        Map<String, List<HrmEmployeeRespDTO>> employeesByName = employeeApi.getEmployeeList().stream()
                .filter(employee -> StrUtil.isNotBlank(employee.getName()))
                .collect(Collectors.groupingBy(employee -> employee.getName().trim()));

        List<PreparedRow> preparedRows = new ArrayList<>();
        List<EamAssetImportPreviewRespVO.Row> responseRows = new ArrayList<>();
        for (EamAssetLedgerParser.LedgerRow source : sourceRows) {
            EamCategoryDO category = categories.get(source.categoryCode());
            if (category == null) {
                List<String> errors = new ArrayList<>(source.errors());
                errors.add("分类编码不存在");
                responseRows.add(EamAssetImportPreviewRespVO.Row.builder().rowNum(source.rowNum())
                        .assetCode(source.assetCode()).name(source.assetName()).categoryName(source.categoryCode())
                        .action("ERROR").mappedFields(source.mappedFields()).warnings(source.warnings())
                        .errors(errors).build());
                continue;
            }
            List<String> warnings = new ArrayList<>(source.warnings());
            List<String> errors = new ArrayList<>(source.errors());
            HrmEmployeeRespDTO matchedEmployee = matchEmployee(source.useUserName(), employeesByName, warnings, "使用人");
            HrmEmployeeRespDTO matchedSupervisor = matchEmployee(source.supervisorName(), employeesByName, warnings, "直属上级");
            EamAssetDO existing = StrUtil.isBlank(source.assetCode())
                    ? null : assetsByCode.get(source.assetCode());
            String action = importedRows.containsKey(source.rowNum()) ? "SKIP_SAME_FILE"
                    : existing == null ? "CREATE" : updateExisting ? "UPDATE" : "SKIP_EXISTING";
            int managementMode = category.getManagementMode() != null
                    ? category.getManagementMode() : EamManagementModeEnum.SERIALIZED.getMode();
            int quantity = EamManagementModeEnum.isBatch(managementMode) && source.quantity() != null
                    ? source.quantity() : 1;
            List<String> defaultedFields = new ArrayList<>(source.defaultedFields());
            if (StrUtil.isBlank(source.assetCode())) {
                defaultedFields.add("资产标签为空，提交时按分类编号规则生成");
            }
            if (EamManagementModeEnum.isBatch(managementMode) && source.quantity() == null) {
                errors.add("批量管理资产数量必须为正整数");
            }
            if (!EamManagementModeEnum.isBatch(managementMode)
                    && !java.util.Objects.equals(source.quantity(), 1)) {
                defaultedFields.add("单件管理资产数量固定为 1");
            }
            NormalizedExtFields normalized = new NormalizedExtFields(Map.of(), Map.of(), Map.of());
            try {
                normalized = categoryFieldService
                        .validateAndNormalizeExtFieldsWithSnapshots(category.getId(), source.extFields());
            } catch (ServiceException e) {
                errors.add(e.getMessage());
            }
            SourceSelection sourceSelection = resolveSource(source.mappedFields().get("资产来源"), errors);
            if (!errors.isEmpty()) action = "ERROR";
            EamAssetImportPreviewRespVO.Row previewRow = EamAssetImportPreviewRespVO.Row.builder()
                    .rowNum(source.rowNum()).assetCode(source.assetCode()).name(source.assetName())
                    .categoryName(category.getName())
                    .managementMode(managementMode).quantity(quantity).useUserName(source.useUserName())
                    .supervisorName(source.supervisorName())
                    .matchedUserName(matchedEmployee != null ? matchedEmployee.getName() : null)
                    .matchedSupervisorName(matchedSupervisor != null ? matchedSupervisor.getName() : null)
                    .action(action).mappedFields(source.mappedFields()).defaultedFields(defaultedFields)
                    .warnings(warnings).errors(errors).build();
            responseRows.add(previewRow);
            preparedRows.add(new PreparedRow(source, category, matchedEmployee, matchedSupervisor, existing, previewRow,
                    normalized, sourceSelection));
        }
        EamAssetImportPreviewRespVO response = buildResponse(fileHash, responseRows);
        response.setErrorCount(responseRows.stream().mapToInt(row -> row.getErrors().size()).sum());
        return new PreparedImport(response, preparedRows);
    }

    private EamAssetDO buildAsset(PreparedRow item) {
        EamAssetLedgerParser.LedgerRow row = item.source();
        EamCategoryDO category = item.category();
        EamAssetDO asset = new EamAssetDO();
        asset.setAssetCode(StrUtil.trim(row.assetCode()));
        asset.setName(StrUtil.blankToDefault(row.assetName(), category.getName()));
        asset.setCategoryId(category.getId());
        asset.setStatus(row.status());
        Integer managementMode = category.getManagementMode() != null
                ? category.getManagementMode() : EamManagementModeEnum.SERIALIZED.getMode();
        asset.setManagementMode(managementMode);
        asset.setQuantity(EamManagementModeEnum.isBatch(managementMode) ? row.quantity() : 1);
        asset.setUnit(StrUtil.blankToDefault(category.getUnit(), "个"));
        asset.setBrand(StrUtil.emptyToNull(row.brand()));
        asset.setBarcode(StrUtil.emptyToNull(row.barcode()));
        asset.setSn(StrUtil.emptyToNull(row.sn()));
        asset.setPurchaseDate(row.purchaseDate());
        asset.setOriginalValue(parseDecimal(row.mappedFields().get("原值")));
        asset.setNetValue(parseDecimal(row.mappedFields().get("净值")));
        asset.setSource(item.sourceSelection().value());
        asset.setSourceLabelSnapshot(item.sourceSelection().label());
        asset.setWarrantyDate(EamAssetLedgerParser.parseDate(String.valueOf(row.mappedFields().getOrDefault("保修到期日", ""))));
        asset.setExpectedLife(parseInteger(row.mappedFields().get("预计使用年限（月）")));
        asset.setLocation(StrUtil.emptyToNull(row.location()));
        asset.setRemark(StrUtil.emptyToNull(row.remark()));
        asset.setUseEmployeeNameSnapshot(StrUtil.emptyToNull(row.useUserName()));
        if (item.matchedEmployee() != null) {
            asset.setUseEmployeeId(item.matchedEmployee().getId());
            asset.setUseDeptId(item.matchedEmployee().getDeptId());
        }
        asset.setSupervisorNameSnapshot(StrUtil.emptyToNull(row.supervisorName()));
        if (item.matchedSupervisor() != null) {
            asset.setSupervisorEmployeeId(item.matchedSupervisor().getId());
        }
        NormalizedExtFields normalized = item.normalizedExtFields();
        asset.setExtFields(normalized.values());
        asset.setExtFieldLabels(normalized.labels());
        asset.setExtFieldDictTypes(normalized.dictTypes());
        return asset;
    }

    private static java.math.BigDecimal parseDecimal(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) return null;
        try { return new java.math.BigDecimal(String.valueOf(value)); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInteger(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) return null;
        try { return new java.math.BigDecimal(String.valueOf(value)).intValueExact(); } catch (Exception e) { return null; }
    }

    private SourceSelection resolveSource(Object raw, List<String> errors) {
        if (raw == null || StrUtil.isBlank(String.valueOf(raw))) return new SourceSelection(null, null);
        String text = String.valueOf(raw).trim();
        List<DictDataRespDTO> options = dictDataApi.getDictDataList("eam_asset_source");
        return options.stream().filter(item -> text.equals(item.getValue()) || text.equals(item.getLabel()))
                .findFirst().map(item -> {
                    try { return new SourceSelection(Integer.valueOf(item.getValue()), item.getLabel()); }
                    catch (NumberFormatException e) { errors.add("资产来源字典值必须为整数"); return new SourceSelection(null, null); }
                }).orElseGet(() -> { errors.add("资产来源字典值无效"); return new SourceSelection(null, null); });
    }

    private static HrmEmployeeRespDTO matchEmployee(String name, Map<String, List<HrmEmployeeRespDTO>> employees,
                                                    List<String> warnings, String label) {
        if (StrUtil.isBlank(name)) return null;
        List<HrmEmployeeRespDTO> matches = employees.getOrDefault(name.trim(), List.of());
        if (matches.size() == 1) return matches.get(0);
        warnings.add(matches.isEmpty() ? label + "未匹配 HRM 员工，已保留姓名快照"
                : label + "存在重名，未自动关联 HRM 员工");
        return null;
    }

    private void writeHistory(PreparedRow item, EamAssetDO asset, Long batchId) {
        // V3 台账不再承载核对、交接和迁移历史字段；历史表保留供业务操作使用。
    }

    private static EamAssetImportPreviewRespVO buildResponse(String fileHash,
                                                               List<EamAssetImportPreviewRespVO.Row> rows) {
        Map<String, Integer> actions = new HashMap<>();
        rows.forEach(row -> actions.merge(row.getAction(), 1, Integer::sum));
        int warnings = rows.stream().map(EamAssetImportPreviewRespVO.Row::getWarnings)
                .mapToInt(Collection::size).sum();
        return EamAssetImportPreviewRespVO.builder().fileHash(fileHash).totalRows(rows.size())
                .createCount(actions.getOrDefault("CREATE", 0))
                .updateCount(actions.getOrDefault("UPDATE", 0))
                .skipCount(actions.getOrDefault("SKIP_EXISTING", 0)
                        + actions.getOrDefault("SKIP_SAME_FILE", 0))
                .warningCount(warnings).rows(rows).build();
    }

    private record PreparedRow(EamAssetLedgerParser.LedgerRow source, EamCategoryDO category,
                               HrmEmployeeRespDTO matchedEmployee, HrmEmployeeRespDTO matchedSupervisor,
                               EamAssetDO existingAsset,
                               EamAssetImportPreviewRespVO.Row previewRow,
                               NormalizedExtFields normalizedExtFields, SourceSelection sourceSelection) {}
    private record SourceSelection(Integer value, String label) {}
    private record PreparedImport(EamAssetImportPreviewRespVO response, List<PreparedRow> rows) {}

}
