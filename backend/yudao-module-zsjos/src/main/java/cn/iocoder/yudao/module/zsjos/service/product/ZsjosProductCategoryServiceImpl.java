package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ZsjosProductCategoryServiceImpl implements ZsjosProductCategoryService {
    private static final int MAX_DEPTH = 10;
    @Resource private ZsjosProductCategoryMapper categoryMapper;
    @Resource private ZsjosProductMapper productMapper;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(ZsjosProductCategorySaveReqVO reqVO) {
        validateCashbackRule(reqVO);
        Long parentId = reqVO.getParentId() == null ? 0L : reqVO.getParentId();
        int level = resolveChildLevel(parentId, null);
        validateName(parentId, reqVO.getName(), null);
        ZsjosProductCategoryDO category = BeanUtils.toBean(reqVO, ZsjosProductCategoryDO.class);
        category.setParentId(parentId); category.setLevel(level);
        categoryMapper.insert(category); return category.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void update(ZsjosProductCategorySaveReqVO reqVO) {
        validateCashbackRule(reqVO);
        ZsjosProductCategoryDO current = validateExists(reqVO.getId());
        Long parentId = reqVO.getParentId() == null ? 0L : reqVO.getParentId();
        int newLevel = current.getLevel();
        List<ZsjosProductCategoryDO> all = categoryMapper.selectList();
        if (!Objects.equals(current.getParentId(), parentId)) {
            newLevel = resolveChildLevel(parentId, current.getId());
            Set<Long> descendants = collectDescendantIds(current.getId(), all);
            if (descendants.contains(parentId)) throw exception(PRODUCT_CATEGORY_LEVEL_INVALID);
            int subtreeHeight = all.stream().filter(item -> descendants.contains(item.getId()))
                    .mapToInt(item -> item.getLevel() - current.getLevel()).max().orElse(0);
            if (newLevel + subtreeHeight > MAX_DEPTH) throw exception(PRODUCT_CATEGORY_LEVEL_INVALID);
        }
        validateName(parentId, reqVO.getName(), current.getId());
        ZsjosProductCategoryDO update = BeanUtils.toBean(reqVO, ZsjosProductCategoryDO.class);
        update.setParentId(parentId); update.setLevel(newLevel);
        categoryMapper.updateById(update);
        if (newLevel != current.getLevel()) {
            int delta = newLevel - current.getLevel();
            for (ZsjosProductCategoryDO item : all) {
                if (!Objects.equals(item.getId(), current.getId())
                        && collectAncestorIds(item.getId(), all).contains(current.getId())) {
                    ZsjosProductCategoryDO descendant = new ZsjosProductCategoryDO();
                    descendant.setId(item.getId()); descendant.setLevel(item.getLevel() + delta);
                    categoryMapper.updateById(descendant);
                }
            }
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ZsjosProductCategoryDO current = validateExists(id);
        if (categoryMapper.selectCountByParentId(id) > 0 || productMapper.selectCountByCategoryId(id) > 0) {
            throw exception(PRODUCT_CATEGORY_IN_USE);
        }
        categoryMapper.deleteById(current.getId());
    }

    @Override public void updateStatus(Long id, Integer status) {
        if (!CommonStatusEnum.ENABLE.getStatus().equals(status) && !CommonStatusEnum.DISABLE.getStatus().equals(status)) {
            throw exception(PRODUCT_CATEGORY_STATUS_INVALID);
        }
        validateExists(id);
        ZsjosProductCategoryDO update = new ZsjosProductCategoryDO(); update.setId(id); update.setStatus(status);
        categoryMapper.updateById(update);
    }

    @Override public ZsjosProductCategoryRespVO get(Long id) {
        return BeanUtils.toBean(validateExists(id), ZsjosProductCategoryRespVO.class);
    }

    @Override public List<ZsjosProductCategoryRespVO> getTree() {
        List<ZsjosProductCategoryDO> all = categoryMapper.selectList();
        Map<Long, ZsjosProductCategoryRespVO> map = new LinkedHashMap<>();
        all.sort(Comparator.comparing(ZsjosProductCategoryDO::getSort).thenComparing(ZsjosProductCategoryDO::getId));
        all.forEach(item -> {
            ZsjosProductCategoryRespVO vo = BeanUtils.toBean(item, ZsjosProductCategoryRespVO.class);
            vo.setHasProducts(productMapper.selectCountByCategoryId(item.getId()) > 0);
            map.put(item.getId(), vo);
        });
        List<ZsjosProductCategoryRespVO> roots = new ArrayList<>();
        for (ZsjosProductCategoryRespVO item : map.values()) {
            if (item.getParentId() == 0) roots.add(item);
            else { ZsjosProductCategoryRespVO parent = map.get(item.getParentId()); if (parent != null) { if (parent.getChildren() == null) parent.setChildren(new ArrayList<>()); parent.getChildren().add(item); } }
        }
        return roots;
    }

    private ZsjosProductCategoryDO validateExists(Long id) {
        ZsjosProductCategoryDO item = categoryMapper.selectById(id);
        if (item == null) throw exception(PRODUCT_CATEGORY_NOT_EXISTS);
        return item;
    }
    private void validateCashbackRule(ZsjosProductCategorySaveReqVO request) {
        if ((request.getDefaultValidCashbackAmount() != null && request.getDefaultValidCashbackAmount().signum() < 0)
                || (request.getDefaultDealCashbackRate() != null
                && (request.getDefaultDealCashbackRate().signum() < 0
                || request.getDefaultDealCashbackRate().compareTo(java.math.BigDecimal.ONE) > 0))) {
            throw exception(CASHBACK_RULE_NOT_CONFIGURED);
        }
    }
    private int resolveChildLevel(Long parentId, Long selfId) {
        if (parentId == 0) return 1;
        ZsjosProductCategoryDO parent = validateExists(parentId);
        if (Objects.equals(parent.getId(), selfId)
                || !CommonStatusEnum.ENABLE.getStatus().equals(parent.getStatus())
                || parent.getLevel() >= MAX_DEPTH
                || productMapper.selectCountByCategoryId(parentId) > 0) {
            throw exception(PRODUCT_CATEGORY_LEVEL_INVALID);
        }
        return parent.getLevel() + 1;
    }
    private Set<Long> collectDescendantIds(Long rootId, List<ZsjosProductCategoryDO> all) {
        Set<Long> result = new HashSet<>();
        result.add(rootId);
        boolean changed;
        do {
            changed = false;
            for (ZsjosProductCategoryDO item : all) {
                if (result.contains(item.getParentId()) && result.add(item.getId())) changed = true;
            }
        } while (changed);
        return result;
    }
    private Set<Long> collectAncestorIds(Long id, List<ZsjosProductCategoryDO> all) {
        Map<Long, ZsjosProductCategoryDO> byId = new HashMap<>();
        all.forEach(item -> byId.put(item.getId(), item));
        Set<Long> result = new HashSet<>();
        ZsjosProductCategoryDO current = byId.get(id);
        while (current != null && current.getParentId() != 0 && result.add(current.getParentId())) {
            current = byId.get(current.getParentId());
        }
        return result;
    }
    private void validateName(Long parentId, String name, Long selfId) {
        ZsjosProductCategoryDO same = categoryMapper.selectByParentIdAndName(parentId, name);
        if (same != null && !Objects.equals(same.getId(), selfId)) throw exception(PRODUCT_CATEGORY_NAME_DUPLICATE);
    }
}
