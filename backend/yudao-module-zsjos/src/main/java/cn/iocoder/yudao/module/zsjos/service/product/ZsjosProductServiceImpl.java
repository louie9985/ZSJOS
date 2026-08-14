package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductSkuMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ZsjosProductServiceImpl implements ZsjosProductService {
    private static final int MAX_DEPTH = 10;
    @Resource private ZsjosProductMapper productMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;
    @Resource private ZsjosProductCategoryMapper categoryMapper;
    @Resource private ZsjosProductSkuMapper skuMapper;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long createProduct(ZsjosProductSaveReqVO reqVO) {
        validateCashbackRule(reqVO.getValidCashbackAmount(), reqVO.getDealCashbackRate());
        ZsjosProductCategoryDO category = validateLeafCategory(reqVO.getCategoryId());
        if (CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())) availablePath(category.getId(), true);
        validateProductName(category.getId(), reqVO.getName(), null);
        ZsjosProductDO product = BeanUtils.toBean(reqVO, ZsjosProductDO.class);
        product.setCategoryId(category.getId());
        product.setProductRef("course_" + UUID.randomUUID().toString().replace("-", ""));
        productMapper.insert(product);
        return product.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateProduct(ZsjosProductSaveReqVO reqVO) {
        validateCashbackRule(reqVO.getValidCashbackAmount(), reqVO.getDealCashbackRate());
        ZsjosProductDO existing = validateExists(reqVO.getId());
        validateLeafCategory(reqVO.getCategoryId());
        if (CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())) availablePath(reqVO.getCategoryId(), true);
        validateProductName(reqVO.getCategoryId(), reqVO.getName(), existing.getId());
        ZsjosProductDO update = BeanUtils.toBean(reqVO, ZsjosProductDO.class);
        update.setProductRef(existing.getProductRef());
        productMapper.updateById(update);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        ZsjosProductDO product = validateExists(id);
        if (intendedProductMapper.selectCountByProductRef(product.getProductRef()) > 0
                || skuMapper.selectCountBySpuId(id) > 0) throw exception(PRODUCT_IN_USE);
        productMapper.deleteById(id);
    }

    @Override public void updateStatus(ZsjosProductStatusReqVO reqVO) {
        if (!Set.of(CommonStatusEnum.ENABLE.getStatus(), CommonStatusEnum.DISABLE.getStatus()).contains(reqVO.getStatus())) {
            throw exception(PRODUCT_NOT_ENABLE);
        }
        ZsjosProductDO existing = validateExists(reqVO.getId());
        if (CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())) availablePath(existing.getCategoryId(), true);
        ZsjosProductDO update = new ZsjosProductDO(); update.setId(reqVO.getId()); update.setStatus(reqVO.getStatus());
        productMapper.updateById(update);
    }

    @Override public ZsjosProductRespVO getProduct(Long id) {
        ZsjosProductRespVO result = BeanUtils.toBean(validateExists(id), ZsjosProductRespVO.class);
        fillPath(result); return result;
    }

    @Override public PageResult<ZsjosProductRespVO> getProductPage(ZsjosProductPageReqVO reqVO) {
        PageResult<ZsjosProductRespVO> page = BeanUtils.toBean(productMapper.selectPage(reqVO), ZsjosProductRespVO.class);
        page.getList().forEach(this::fillPath); return page;
    }

    @Override public List<ZsjosProductSimpleRespVO> getEnabledSimpleList() {
        List<ZsjosProductSimpleRespVO> result = new ArrayList<>();
        for (ZsjosProductDO item : productMapper.selectEnabledList()) {
            CategoryPath path = availablePath(item.getCategoryId(), false);
            if (path != null) result.add(toSimple(item, path));
        }
        return result;
    }

    @Override public List<LeadProductSnapshot> validateEnabledProducts(Collection<String> refs) {
        if (refs == null || refs.isEmpty() || new HashSet<>(refs).size() != refs.size()) throw exception(PRODUCT_REFS_INVALID);
        Map<String, ZsjosProductDO> byRef = new HashMap<>();
        productMapper.selectListByRefs(refs).forEach(item -> byRef.put(item.getProductRef(), item));
        List<LeadProductSnapshot> result = new ArrayList<>();
        for (String ref : refs) {
            ZsjosProductDO product = byRef.get(ref);
            if (product == null || !CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus())) throw exception(PRODUCT_REFS_INVALID);
            CategoryPath path = availablePath(product.getCategoryId(), true);
            result.add(LeadProductSnapshot.of(product.getProductRef(), product.getName(), path.nodes()));
        }
        return result;
    }

    private ZsjosProductDO validateExists(Long id) {
        ZsjosProductDO product = productMapper.selectById(id);
        if (product == null) throw exception(PRODUCT_NOT_EXISTS);
        return product;
    }

    private void validateCashbackRule(java.math.BigDecimal amount, java.math.BigDecimal rate) {
        if ((amount != null && amount.signum() < 0) || (rate != null && (rate.signum() < 0
                || rate.compareTo(java.math.BigDecimal.ONE) > 0))) throw exception(CASHBACK_RULE_NOT_CONFIGURED);
    }

    private ZsjosProductCategoryDO validateLeafCategory(Long categoryId) {
        ZsjosProductCategoryDO category = categoryMapper.selectById(categoryId);
        if (category == null || categoryMapper.selectCountByParentId(categoryId) > 0) throw exception(PRODUCT_CATEGORY_LEVEL_INVALID);
        return category;
    }

    private void validateProductName(Long categoryId, String name, Long selfId) {
        ZsjosProductDO same = productMapper.selectByCategoryIdAndName(categoryId, name);
        if (same != null && !Objects.equals(same.getId(), selfId)) throw exception(PRODUCT_REF_DUPLICATE);
    }

    private CategoryPath availablePath(Long categoryId, boolean fail) {
        if (categoryId == null || categoryMapper.selectCountByParentId(categoryId) > 0) return pathFailure(fail);
        List<ZsjosProductCategoryPathNodeVO> reversed = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long currentId = categoryId;
        while (currentId != null && currentId != 0 && reversed.size() < MAX_DEPTH) {
            if (!visited.add(currentId)) return pathFailure(fail);
            ZsjosProductCategoryDO current = categoryMapper.selectById(currentId);
            if (current == null || !CommonStatusEnum.ENABLE.getStatus().equals(current.getStatus())) return pathFailure(fail);
            reversed.add(new ZsjosProductCategoryPathNodeVO(current.getId(), current.getName()));
            currentId = current.getParentId();
        }
        if (reversed.isEmpty() || (currentId != null && currentId != 0)) return pathFailure(fail);
        Collections.reverse(reversed);
        return new CategoryPath(List.copyOf(reversed));
    }

    private CategoryPath pathFailure(boolean fail) {
        if (fail) throw exception(PRODUCT_REFS_INVALID);
        return null;
    }

    private ZsjosProductSimpleRespVO toSimple(ZsjosProductDO item, CategoryPath path) {
        List<ZsjosProductCategoryPathNodeVO> nodes = path.nodes();
        ZsjosProductCategoryPathNodeVO leaf = nodes.getLast();
        ZsjosProductCategoryPathNodeVO first = nodes.getFirst();
        ZsjosProductCategoryPathNodeVO second = nodes.size() > 1 ? nodes.get(1) : null;
        return new ZsjosProductSimpleRespVO(item.getProductRef(), item.getName(), leaf.id(), leaf.name(), nodes,
                first.id(), first.name(), second == null ? null : second.id(), second == null ? null : second.name());
    }

    private void fillPath(ZsjosProductRespVO vo) {
        CategoryPath path = availablePath(vo.getCategoryId(), false);
        if (path == null) return;
        List<ZsjosProductCategoryPathNodeVO> nodes = path.nodes();
        ZsjosProductCategoryPathNodeVO leaf = nodes.getLast();
        vo.setCategoryName(leaf.name()); vo.setCategoryPath(nodes);
        vo.setLevel1CategoryId(nodes.getFirst().id()); vo.setLevel1CategoryName(nodes.getFirst().name());
        if (nodes.size() > 1) { vo.setLevel2CategoryId(nodes.get(1).id()); vo.setLevel2CategoryName(nodes.get(1).name()); }
    }

    private record CategoryPath(List<ZsjosProductCategoryPathNodeVO> nodes) {}
}
