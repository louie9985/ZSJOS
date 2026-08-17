package cn.iocoder.yudao.module.eam.service.asset;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportExcelVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.coderule.EamCodeRuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_IMPORT_LIST_EMPTY;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_NOT_EXISTS;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_STATUS_INVALID;

/**
 * EAM 资产 Service 实现类
 */
@Service
@Validated
public class EamAssetServiceImpl implements EamAssetService {

    @Resource
    private EamAssetMapper assetMapper;
    @Resource
    private EamCategoryService categoryService;
    @Resource
    private EamCategoryFieldService categoryFieldService;
    @Resource
    private EamCodeRuleService codeRuleService;
    @Resource
    private EamAssetChangeLogService changeLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAsset(EamAssetSaveReqVO reqVO) {
        // 1. 校验分类与自定义字段
        EamCategoryDO category = categoryService.validateCategoryExists(reqVO.getCategoryId());
        Map<String, Object> extFields =
                categoryFieldService.validateAndNormalizeExtFields(reqVO.getCategoryId(), reqVO.getExtFields());

        // 2. 组装资产，新建资产一律以「闲置」入账，归属由后续流转单驱动
        EamAssetDO asset = BeanUtils.toBean(reqVO, EamAssetDO.class);
        asset.setExtFields(extFields);
        asset.setStatus(EamAssetStatusEnum.IDLE.getStatus());
        applyManagementSnapshot(asset, category, reqVO.getQuantity());
        // 3. 导入可沿用已有标签；普通建档仍由编号规则生成
        if (StrUtil.isBlank(reqVO.getAssetCode())) {
            asset.setAssetCode(codeRuleService.generateAssetCode(reqVO.getCategoryId()));
        } else {
            String assetCode = reqVO.getAssetCode().trim();
            if (assetMapper.selectByAssetCode(assetCode) != null) {
                throw exception(ASSET_CODE_DUPLICATE);
            }
            asset.setAssetCode(assetCode);
        }
        assetMapper.insert(asset);

        // 4. 记录建档流水
        changeLogService.record(null, asset, EamChangeTypeEnum.CREATE.getType(), null,
                StrUtil.format("建档，资产编号 {}", asset.getAssetCode()));
        return asset.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAsset(EamAssetSaveReqVO reqVO) {
        EamAssetDO before = validateAssetExists(reqVO.getId());
        EamCategoryDO category = categoryService.validateCategoryExists(reqVO.getCategoryId());
        Map<String, Object> extFields =
                categoryFieldService.validateAndNormalizeExtFields(reqVO.getCategoryId(), reqVO.getExtFields());

        // 状态与资产编号不通过编辑表单变更：状态归状态机，编号归编号规则
        EamAssetDO updateObj = BeanUtils.toBean(reqVO, EamAssetDO.class);
        updateObj.setExtFields(extFields);
        applyManagementSnapshot(updateObj, category, reqVO.getQuantity());
        updateObj.setStatus(null);
        updateObj.setAssetCode(null);
        assetMapper.updateById(updateObj);

        EamAssetDO after = assetMapper.selectById(reqVO.getId());
        changeLogService.record(before, after, EamChangeTypeEnum.EDIT.getType(), null, "编辑资产信息");
    }

    @Override
    public void deleteAsset(Long id) {
        validateAssetExists(id);
        assetMapper.deleteById(id);
    }

    @Override
    public EamAssetDO getAsset(Long id) {
        return assetMapper.selectById(id);
    }

    @Override
    public List<EamAssetDO> getAssetList(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return assetMapper.selectByIds(ids);
    }

    @Override
    public PageResult<EamAssetDO> getAssetPage(EamAssetPageReqVO reqVO) {
        return assetMapper.selectPage(reqVO);
    }

    @Override
    public EamAssetDO validateAssetExists(Long id) {
        EamAssetDO asset = assetMapper.selectById(id);
        if (asset == null) {
            throw exception(ASSET_NOT_EXISTS);
        }
        return asset;
    }

    @Override
    public void validateStatusTransition(EamAssetDO asset, Set<Integer> allowedStatuses) {
        if (!allowedStatuses.contains(asset.getStatus())) {
            throw exception(ASSET_STATUS_INVALID, asset.getAssetCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyChange(Long assetId, Integer newStatus, Long newUserId, Long newDeptId,
                            Integer changeType, Long bizId, String content) {
        EamAssetDO before = validateAssetExists(assetId);

        EamAssetDO updateObj = new EamAssetDO();
        updateObj.setId(assetId);
        if (newStatus != null) {
            updateObj.setStatus(newStatus);
            // 进入维修/待报废/冻结这类可逆中间态时，留存原状态供恢复
            if (isReversibleStatus(newStatus)) {
                updateObj.setPreviousStatus(before.getStatus());
            }
        }
        if (newUserId != null) {
            updateObj.setUseUserId(newUserId);
        }
        if (newDeptId != null) {
            updateObj.setUseDeptId(newDeptId);
        }
        assetMapper.updateById(updateObj);

        EamAssetDO after = assetMapper.selectById(assetId);
        changeLogService.record(before, after, changeType, bizId, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markLost(Long assetId, Long inventoryId, String remark) {
        EamAssetDO asset = validateAssetExists(assetId);
        validateStatusTransition(asset, EamAssetStatusEnum.ALLOW_LOST);
        applyChange(assetId, EamAssetStatusEnum.LOST.getStatus(), null, null,
                EamChangeTypeEnum.LOST.getType(), inventoryId,
                StrUtil.blankToDefault(remark, "盘点未找到，标记丢失"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long assetId, String reason) {
        EamAssetDO asset = validateAssetExists(assetId);
        validateStatusTransition(asset, EamAssetStatusEnum.ALLOW_FREEZE);
        applyChange(assetId, EamAssetStatusEnum.FROZEN.getStatus(), null, null,
                EamChangeTypeEnum.FREEZE.getType(), null,
                StrUtil.blankToDefault(reason, "冻结资产"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long assetId, String reason) {
        EamAssetDO asset = validateAssetExists(assetId);
        validateStatusTransition(asset, EamAssetStatusEnum.ALLOW_UNFREEZE);
        // 解冻恢复到冻结前状态；缺失记录时退回闲置，避免资产卡在冻结态
        Integer restored = asset.getPreviousStatus() != null
                ? asset.getPreviousStatus() : EamAssetStatusEnum.IDLE.getStatus();
        applyChange(assetId, restored, null, null,
                EamChangeTypeEnum.UNFREEZE.getType(), null,
                StrUtil.blankToDefault(reason, "解冻资产"));
    }

    @Override
    public List<EamAssetDO> getAssetListByScope(Integer scopeType, String scopeValue) {
        EamAssetPageReqVO query = new EamAssetPageReqVO();
        query.setPageNo(1);
        query.setPageSize(Integer.MAX_VALUE);
        if (scopeType != null && scopeValue != null && !scopeValue.isBlank()) {
            switch (scopeType) {
                case 2 -> query.setUseDeptId(Long.valueOf(scopeValue.split(",")[0].trim()));
                case 3 -> query.setCategoryId(Long.valueOf(scopeValue.split(",")[0].trim()));
                default -> { /* 1 全部 / 4 存放地点在下方按内存过滤 */ }
            }
        }
        List<EamAssetDO> assets = assetMapper.selectPage(query).getList();
        // 已报废资产不纳入盘点范围
        assets.removeIf(a -> EamAssetStatusEnum.TERMINAL_STATUSES.contains(a.getStatus()));
        if (scopeType != null && scopeType == 4 && StrUtil.isNotBlank(scopeValue)) {
            Set<String> locations = Set.copyOf(Arrays.asList(scopeValue.split(",")));
            assets.removeIf(a -> a.getLocation() == null
                    || locations.stream().noneMatch(loc -> a.getLocation().contains(loc.trim())));
        }
        return assets;
    }

    @Override
    public String buildQrContent(Long assetId) {
        EamAssetDO asset = validateAssetExists(assetId);
        return asset.getAssetCode();
    }

    @Override
    public EamAssetImportRespVO importAssetList(List<EamAssetImportExcelVO> list) {
        if (CollUtil.isEmpty(list)) {
            throw exception(ASSET_IMPORT_LIST_EMPTY);
        }
        // 分类编码 → 分类 ID，避免逐行查库
        Map<String, Long> categoryCodeMap = categoryService.getCategoryList().stream()
                .filter(c -> StrUtil.isNotBlank(c.getCode()))
                .collect(Collectors.toMap(EamCategoryDO::getCode, EamCategoryDO::getId, (a, b) -> a));

        EamAssetImportRespVO resp = EamAssetImportRespVO.builder().build();
        for (int i = 0; i < list.size(); i++) {
            EamAssetImportExcelVO row = list.get(i);
            int rowNum = i + 2; // Excel 首行是表头
            try {
                if (StrUtil.isBlank(row.getName())) {
                    throw new IllegalArgumentException("资产名称不能为空");
                }
                Long categoryId = categoryCodeMap.get(StrUtil.trimToEmpty(row.getCategoryCode()));
                if (categoryId == null) {
                    throw new IllegalArgumentException(
                            StrUtil.format("分类编码【{}】不存在", row.getCategoryCode()));
                }
                EamAssetSaveReqVO reqVO = BeanUtils.toBean(row, EamAssetSaveReqVO.class);
                reqVO.setCategoryId(categoryId);
                reqVO.setExtFields(Map.of());

                // 每行单独一个事务：失败行不影响已导入的行
                Long assetId = self().createAsset(reqVO);
                resp.getCreateAssetCodes().add(assetMapper.selectById(assetId).getAssetCode());
            } catch (Exception e) {
                resp.getFailures().add(EamAssetImportRespVO.FailureItem.builder()
                        .rowNum(rowNum)
                        .name(row.getName())
                        .reason(resolveFailureReason(e))
                        .build());
            }
        }
        return resp;
    }

    /**
     * 业务异常取其可读消息，其余异常统一兜底，避免把堆栈细节透给使用者
     */
    private String resolveFailureReason(Exception e) {
        if (e instanceof ServiceException se) {
            return se.getMessage();
        }
        if (e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        return "导入失败：" + e.getClass().getSimpleName();
    }

    /**
     * 取自身代理，保证逐行 createAsset 各自开启事务
     */
    private EamAssetService self() {
        return SpringUtil.getBean(EamAssetService.class);
    }

    private void applyManagementSnapshot(EamAssetDO asset, EamCategoryDO category, Integer quantity) {
        Integer mode = category.getManagementMode() != null
                ? category.getManagementMode() : EamManagementModeEnum.SERIALIZED.getMode();
        asset.setManagementMode(mode);
        asset.setUnit(StrUtil.blankToDefault(category.getUnit(), "个"));
        asset.setQuantity(EamManagementModeEnum.isBatch(mode) && quantity != null && quantity > 0 ? quantity : 1);
    }

    /**
     * 维修中 / 待报废 / 冻结属于可逆中间态，需要保留原状态以便恢复
     */
    private boolean isReversibleStatus(Integer status) {
        return EamAssetStatusEnum.REPAIRING.getStatus().equals(status)
                || EamAssetStatusEnum.PENDING_SCRAP.getStatus().equals(status)
                || EamAssetStatusEnum.FROZEN.getStatus().equals(status);
    }

}
