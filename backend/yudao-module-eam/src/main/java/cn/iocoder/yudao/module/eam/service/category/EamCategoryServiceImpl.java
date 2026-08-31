package cn.iocoder.yudao.module.eam.service.category;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategorySaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.category.EamCategoryMapper;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamDeliveryModeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.*;

/**
 * EAM 资产分类 Service 实现类
 */
@Service
@Validated
public class EamCategoryServiceImpl implements EamCategoryService {

    @Resource
    private EamCategoryMapper categoryMapper;
    @Resource
    private EamAssetMapper assetMapper;

    @Override
    public Long createCategory(EamCategorySaveReqVO reqVO) {
        // 1. 校验父分类
        validateParentCategory(reqVO.getParentId(), null);
        // 2. 校验编码唯一
        validateCodeUnique(reqVO.getCode(), null);

        // 3. 插入
        EamCategoryDO category = BeanUtils.toBean(reqVO, EamCategoryDO.class);
        normalizeManagement(category);
        validateCategoryPolicy(category);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void updateCategory(EamCategorySaveReqVO reqVO) {
        // 1. 校验存在
        validateCategoryExists(reqVO.getId());
        // 2. 校验父分类（不能设置自己或子分类为父）
        validateParentCategory(reqVO.getParentId(), reqVO.getId());
        // 3. 校验编码唯一
        validateCodeUnique(reqVO.getCode(), reqVO.getId());

        // 4. 更新
        EamCategoryDO updateObj = BeanUtils.toBean(reqVO, EamCategoryDO.class);
        normalizeManagement(updateObj);
        validateCategoryPolicy(updateObj);
        categoryMapper.updateById(updateObj);
    }

    @Override
    public void deleteCategory(Long id) {
        // 1. 校验存在
        validateCategoryExists(id);
        // 2. 校验是否有子分类
        if (categoryMapper.selectCountByParentId(id) > 0) {
            throw exception(CATEGORY_HAS_CHILDREN);
        }
        // 3. 校验是否有关联资产
        if (assetMapper.selectCountByCategoryId(id) > 0) {
            throw exception(CATEGORY_HAS_ASSET);
        }
        // 4. 删除
        categoryMapper.deleteById(id);
    }

    @Override
    public List<EamCategoryDO> getCategoryList() {
        return categoryMapper.selectList();
    }

    @Override
    public EamCategoryDO getCategory(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public EamCategoryDO validateCategoryExists(Long id) {
        EamCategoryDO category = categoryMapper.selectById(id);
        if (category == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
        return category;
    }

    @Override
    public List<EamCategoryDO> getAncestorChain(Long categoryId) {
        List<EamCategoryDO> chain = new ArrayList<>();
        Long currentId = categoryId;
        // 防止无限循环
        int maxDepth = 20;
        while (currentId != null && currentId > 0 && maxDepth-- > 0) {
            EamCategoryDO category = categoryMapper.selectById(currentId);
            if (category == null) {
                break;
            }
            chain.add(category);
            currentId = category.getParentId();
        }
        return chain;
    }

    @Override
    public EamCategoryPolicy getEffectivePolicy(Long categoryId) {
        Integer deliveryMode = null;
        Integer custodyMode = null;
        for (EamCategoryDO category : getAncestorChain(categoryId)) {
            deliveryMode = deliveryMode == null ? category.getDeliveryMode() : deliveryMode;
            custodyMode = custodyMode == null ? category.getCustodyMode() : custodyMode;
        }
        if (deliveryMode == null || custodyMode == null) {
            throw exception(CATEGORY_POLICY_UNCONFIRMED);
        }
        return new EamCategoryPolicy(deliveryMode, custodyMode);
    }

    private void validateParentCategory(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return; // 根分类
        }
        // 父分类必须存在
        EamCategoryDO parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
        // 不能设置自己为父
        if (selfId != null && Objects.equals(parentId, selfId)) {
            throw exception(CATEGORY_PARENT_ERROR);
        }
        // 不能设置子分类为父（避免循环），沿 parent 链向上检查是否经过 selfId
        if (selfId != null) {
            Long checkId = parentId;
            int maxDepth = 20;
            while (checkId != null && checkId > 0 && maxDepth-- > 0) {
                if (Objects.equals(checkId, selfId)) {
                    throw exception(CATEGORY_PARENT_ERROR);
                }
                EamCategoryDO node = categoryMapper.selectById(checkId);
                checkId = node != null ? node.getParentId() : null;
            }
        }
    }

    private void validateCodeUnique(String code, Long excludeId) {
        EamCategoryDO existing = categoryMapper.selectByCode(code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(CATEGORY_CODE_DUPLICATE);
        }
    }

    private void normalizeManagement(EamCategoryDO category) {
        if (!Arrays.asList(EamManagementModeEnum.ARRAYS).contains(category.getManagementMode())) {
            category.setManagementMode(EamManagementModeEnum.SERIALIZED.getMode());
        }
        if (StrUtil.isBlank(category.getUnit())) {
            category.setUnit("个");
        }
    }

    private void validateCategoryPolicy(EamCategoryDO category) {
        boolean root = category.getParentId() == null || category.getParentId() == 0L;
        if (root && (category.getDeliveryMode() == null || category.getCustodyMode() == null)) {
            throw exception(CATEGORY_POLICY_REQUIRED);
        }
        if (category.getDeliveryMode() != null
                && !Arrays.asList(EamDeliveryModeEnum.ARRAYS).contains(category.getDeliveryMode())) {
            throw exception(CATEGORY_POLICY_INVALID);
        }
        if (category.getCustodyMode() != null
                && !Arrays.asList(EamCustodyModeEnum.ARRAYS).contains(category.getCustodyMode())) {
            throw exception(CATEGORY_POLICY_INVALID);
        }
    }

}
