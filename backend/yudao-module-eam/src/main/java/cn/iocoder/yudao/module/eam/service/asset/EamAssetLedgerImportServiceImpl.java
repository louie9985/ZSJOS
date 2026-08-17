package cn.iocoder.yudao.module.eam.service.asset;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
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
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.coderule.EamCodeRuleService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_IMPORT_CATEGORY_MISSING;

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
    private AdminUserApi adminUserApi;

    @Override
    public EamAssetImportPreviewRespVO preview(byte[] content, String fileName, boolean updateExisting) {
        return prepare(content, updateExisting).response();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EamAssetImportPreviewRespVO commit(byte[] content, String fileName, boolean updateExisting) {
        PreparedImport prepared = prepare(content, updateExisting);
        EamAssetImportPreviewRespVO response = prepared.response();
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

        List<EamCategoryDO> categories = categoryService.getCategoryList();
        Map<String, EamCategoryDO> roots = categories.stream()
                .filter(category -> Objects.equals(category.getParentId(), 0L))
                .collect(Collectors.toMap(EamCategoryDO::getName, Function.identity(), (a, b) -> a));
        Map<String, EamCategoryDO> leaves = categories.stream()
                .filter(category -> !Objects.equals(category.getParentId(), 0L))
                .collect(Collectors.toMap(category -> category.getParentId() + "\n" + category.getName(),
                        Function.identity(), (a, b) -> a));

        Map<String, List<AdminUserRespDTO>> usersByNickname = adminUserApi
                .getUserListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
                .filter(user -> StrUtil.isNotBlank(user.getNickname()))
                .collect(Collectors.groupingBy(user -> user.getNickname().trim()));

        List<PreparedRow> preparedRows = new ArrayList<>();
        List<EamAssetImportPreviewRespVO.Row> responseRows = new ArrayList<>();
        for (EamAssetLedgerParser.LedgerRow source : sourceRows) {
            EamCategoryDO root = roots.get(source.rootCategoryName());
            EamCategoryDO category = root == null ? null
                    : leaves.get(root.getId() + "\n" + source.leafCategoryName());
            if (category == null) {
                throw exception(ASSET_IMPORT_CATEGORY_MISSING,
                        source.rootCategoryName(), source.leafCategoryName());
            }
            List<String> warnings = new ArrayList<>(source.warnings());
            AdminUserRespDTO matchedUser = matchUser(source.sourceUserName(), usersByNickname, warnings);
            if (source.extFields().containsKey("source_attachment_names")) {
                warnings.add("附件列仅保留原文件名，未写入附件地址");
            }
            EamAssetDO existing = StrUtil.isBlank(source.assetCode())
                    ? null : assetsByCode.get(source.assetCode());
            String action = importedRows.containsKey(source.rowNum()) ? "SKIP_SAME_FILE"
                    : existing == null ? "CREATE" : updateExisting ? "UPDATE" : "SKIP_EXISTING";
            int managementMode = category.getManagementMode() != null
                    ? category.getManagementMode() : EamManagementModeEnum.SERIALIZED.getMode();
            int quantity = EamManagementModeEnum.isBatch(managementMode) ? source.quantity() : 1;
            List<String> defaultedFields = new ArrayList<>(source.defaultedFields());
            if (StrUtil.isBlank(source.assetCode())) {
                defaultedFields.add("资产标签为空，提交时按分类编号规则生成");
            }
            if (!EamManagementModeEnum.isBatch(managementMode) && source.quantity() != 1) {
                defaultedFields.add("单件管理资产数量固定为 1");
            }
            EamAssetImportPreviewRespVO.Row previewRow = EamAssetImportPreviewRespVO.Row.builder()
                    .rowNum(source.rowNum()).assetCode(source.assetCode()).name(source.assetName())
                    .categoryName(source.rootCategoryName() + " / " + source.leafCategoryName())
                    .managementMode(managementMode).quantity(quantity).sourceUserName(source.sourceUserName())
                    .matchedUserName(matchedUser != null ? matchedUser.getNickname() : null)
                    .action(action).sourceFields(source.sourceFields()).defaultedFields(defaultedFields)
                    .warnings(warnings).build();
            responseRows.add(previewRow);
            preparedRows.add(new PreparedRow(source, category, matchedUser, existing, previewRow));
        }
        return new PreparedImport(buildResponse(fileHash, responseRows), preparedRows);
    }

    private EamAssetDO buildAsset(PreparedRow item) {
        EamAssetLedgerParser.LedgerRow row = item.source();
        EamCategoryDO category = item.category();
        EamAssetDO asset = new EamAssetDO();
        asset.setAssetCode(StrUtil.trim(row.assetCode()));
        asset.setName(StrUtil.blankToDefault(row.assetName(), row.leafCategoryName()));
        asset.setCategoryId(category.getId());
        asset.setStatus(row.status());
        Integer managementMode = category.getManagementMode() != null
                ? category.getManagementMode() : EamManagementModeEnum.SERIALIZED.getMode();
        asset.setManagementMode(managementMode);
        asset.setQuantity(EamManagementModeEnum.isBatch(managementMode) ? row.quantity() : 1);
        asset.setUnit(StrUtil.blankToDefault(category.getUnit(), "个"));
        asset.setBrand(StrUtil.emptyToNull(row.brand()));
        asset.setSn(StrUtil.emptyToNull(row.sn()));
        asset.setPurchaseDate(row.purchaseDate());
        asset.setLocation(StrUtil.emptyToNull(row.location()));
        asset.setRemark(StrUtil.emptyToNull(row.remark()));
        if (item.matchedUser() != null) {
            asset.setUseUserId(item.matchedUser().getId());
            asset.setUseDeptId(item.matchedUser().getDeptId());
        }
        asset.setExtFields(categoryFieldService.validateAndNormalizeExtFields(category.getId(), row.extFields()));
        return asset;
    }

    private static AdminUserRespDTO matchUser(String sourceName, Map<String, List<AdminUserRespDTO>> users,
                                              List<String> warnings) {
        if (StrUtil.isBlank(sourceName)) return null;
        List<AdminUserRespDTO> matches = users.getOrDefault(sourceName.trim(), List.of());
        if (matches.size() == 1) return matches.get(0);
        warnings.add(matches.isEmpty() ? "使用人未匹配系统用户，已保留原表姓名"
                : "使用人存在重名，未自动关联系统用户");
        return null;
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
                               AdminUserRespDTO matchedUser, EamAssetDO existingAsset,
                               EamAssetImportPreviewRespVO.Row previewRow) {}
    private record PreparedImport(EamAssetImportPreviewRespVO response, List<PreparedRow> rows) {}

}
