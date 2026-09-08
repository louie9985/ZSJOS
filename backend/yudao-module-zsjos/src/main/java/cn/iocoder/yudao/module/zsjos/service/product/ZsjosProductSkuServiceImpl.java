package cn.iocoder.yudao.module.zsjos.service.product;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ZsjosProductSkuServiceImpl implements ZsjosProductSkuService {
    @Value("${zsjos.product.sku.max-generated-combinations:500}")
    private int maxGeneratedCombinations;
    @Resource private ZsjosProductService productService;
    @Resource private ZsjosProductMapper productMapper;
    @Resource private ZsjosProductCategoryMapper categoryMapper;
    @Resource private ZsjosProductAttrMapper attrMapper;
    @Resource private ZsjosProductAttrValueMapper attrValueMapper;
    @Resource private ZsjosProductSkuMapper skuMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;
    @Resource private ProductCategoryLocks categoryLocks;

    @Override
    public List<ZsjosProductAttrRespVO> getAttrs(Long spuId) {
        productService.getProduct(spuId);
        List<ZsjosProductAttrDO> attrs = attrMapper.selectListBySpuId(spuId);
        Map<Long, List<ZsjosProductAttrValueDO>> values = attrValueMapper
                .selectListByAttrIds(attrs.stream().map(ZsjosProductAttrDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ZsjosProductAttrValueDO::getAttrId, LinkedHashMap::new, Collectors.toList()));
        return attrs.stream().map(attr -> new ZsjosProductAttrRespVO(attr.getAttrKey(), attr.getAttrName(),
                attr.getRequired(), attr.getSort(), values.getOrDefault(attr.getId(), List.of()).stream()
                .map(value -> new ZsjosProductAttrRespVO.Value(value.getValue(), value.getLabel(), value.getSort())).toList())).toList();
    }

    private List<ZsjosProductAttrRespVO> enabledAttrs(Long spuId) {
        var attrs = attrMapper.selectListBySpuId(spuId).stream()
                .filter(a -> CommonStatusEnum.ENABLE.getStatus().equals(a.getStatus())).toList();
        var values = attrValueMapper.selectListByAttrIds(attrs.stream().map(ZsjosProductAttrDO::getId).toList());
        return attrs.stream().map(a -> new ZsjosProductAttrRespVO(a.getAttrKey(), a.getAttrName(), a.getRequired(), a.getSort(),
                values.stream().filter(v -> Objects.equals(v.getAttrId(), a.getId())
                        && CommonStatusEnum.ENABLE.getStatus().equals(v.getStatus()))
                        .map(v -> new ZsjosProductAttrRespVO.Value(v.getValue(), v.getLabel(), v.getSort())).toList())).toList();
    }

    @Override
    public List<ExamProductScopeRespVO> getExamProductOptions() {
        var enabled = productService.getEnabledSimpleList();
        if (enabled.isEmpty()) return List.of();
        var products = productMapper.selectListByRefs(enabled.stream().map(ZsjosProductSimpleRespVO::productRef).toList());
        var byRef = enabled.stream().collect(Collectors.toMap(ZsjosProductSimpleRespVO::productRef, p -> p));
        List<ExamProductScopeRespVO> result = new ArrayList<>();
        for (int start = 0; start < products.size(); start += 200) {
            var batch = products.subList(start, Math.min(start + 200, products.size()));
            var ids = batch.stream().map(ZsjosProductDO::getId).toList();
            var attrs = attrMapper.selectListBySpuIds(ids).stream()
                    .filter(a -> CommonStatusEnum.ENABLE.getStatus().equals(a.getStatus())).toList();
            var values = attrValueMapper.selectListByAttrIds(attrs.stream().map(ZsjosProductAttrDO::getId).toList()).stream()
                    .filter(v -> CommonStatusEnum.ENABLE.getStatus().equals(v.getStatus()))
                    .collect(Collectors.groupingBy(ZsjosProductAttrValueDO::getAttrId));
            var byProduct = attrs.stream().collect(Collectors.groupingBy(ZsjosProductAttrDO::getSpuId));
            var skus = skuMapper.selectEnabledListBySpuIds(ids).stream().collect(Collectors.groupingBy(ZsjosProductSkuDO::getSpuId));
            for (var product : batch) {
                var metadata = byProduct.getOrDefault(product.getId(), List.of()).stream().map(a ->
                        new ZsjosProductAttrRespVO(a.getAttrKey(), a.getAttrName(), a.getRequired(), a.getSort(),
                                values.getOrDefault(a.getId(), List.of()).stream().map(v ->
                                        new ZsjosProductAttrRespVO.Value(v.getValue(), v.getLabel(), v.getSort())).toList())).toList();
                var scope = examScope(product, byRef.get(product.getProductRef()).categoryPath(), Map.of(), metadata,
                        skus.getOrDefault(product.getId(), List.of()));
                if (!scope.skus().isEmpty()) result.add(scope);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ExamProductScopeRespVO resolveExamScope(Long productId, Map<String, String> selected) {
        var product = lockEnabledProduct(productId);
        var snapshot = productService.validateEnabledProducts(List.of(product.getProductRef())).getFirst();
        var result = examScope(product, snapshot.categoryPath(), selected == null ? Map.of() : selected,
                enabledAttrs(productId), skuMapper.selectEnabledListBySpuIds(List.of(productId)));
        if (result.skus().isEmpty()) throw exception(EXAM_SCHEDULE_SKU_NO_MATCH);
        return result;
    }

    private ExamProductScopeRespVO examScope(ZsjosProductDO product, List<ZsjosProductCategoryPathNodeVO> path,
                                       Map<String, String> selected, List<ZsjosProductAttrRespVO> attrs,
                                       List<ZsjosProductSkuDO> candidates) {
        for (var entry : selected.entrySet()) {
            if (attrs.stream().noneMatch(a -> a.attrKey().equals(entry.getKey())
                    && a.values().stream().anyMatch(v -> Objects.equals(v.value(), entry.getValue())))) {
                throw exception(PRODUCT_ATTR_INVALID);
            }
        }
        var skus = candidates.stream().filter(s -> {
            var values = parseAttrs(s.getAttrValuesJson());
            return selected.entrySet().stream().allMatch(e -> Objects.equals(values.get(e.getKey()), e.getValue()))
                    && values.entrySet().stream().allMatch(e -> attrs.stream().anyMatch(a -> a.attrKey().equals(e.getKey())
                    && a.values().stream().anyMatch(v -> Objects.equals(v.value(), e.getValue()))))
                    && attrs.stream().filter(a -> Boolean.TRUE.equals(a.required())).allMatch(a -> values.containsKey(a.attrKey()));
        }).map(s -> new ExamProductScopeRespVO.Sku(s.getId(), s.getSkuRef(), s.getSkuName(), parseAttrs(s.getAttrValuesJson()),
                ProductSpecVO.resolve(parseAttrs(s.getAttrValuesJson()), attrs))).toList();
        return new ExamProductScopeRespVO(product.getId(), product.getProductRef(), product.getName(), product.getCategoryId(),
                path, attrs, ProductSpecVO.resolve(selected, attrs), skus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void saveAttrs(ZsjosProductAttrSaveReqVO reqVO) {
        lockProduct(reqVO.getSpuId());
        Set<String> names = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (ZsjosProductAttrSaveReqVO.Attr attr : reqVO.getAttrs()) {
            if (!names.add(attr.getAttrName())) throw exception(PRODUCT_ATTR_INVALID);
            String key = attr.getAttrKey() == null || attr.getAttrKey().isBlank()
                    ? "attr_" + UUID.randomUUID().toString().replace("-", "") : attr.getAttrKey();
            attr.setAttrKey(key);
            if (!keys.add(key) || attr.getValues().stream().map(ZsjosProductAttrSaveReqVO.Value::getValue).distinct().count() != attr.getValues().size()) {
                throw exception(PRODUCT_ATTR_INVALID);
            }
        }
        List<ZsjosProductAttrDO> oldAttrs = attrMapper.selectListBySpuId(reqVO.getSpuId());
        if (!oldAttrs.isEmpty()) {
            attrValueMapper.delete(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ZsjosProductAttrValueDO>()
                    .in(ZsjosProductAttrValueDO::getAttrId, oldAttrs.stream().map(ZsjosProductAttrDO::getId).toList()));
            attrMapper.delete(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ZsjosProductAttrDO>()
                    .eq(ZsjosProductAttrDO::getSpuId, reqVO.getSpuId()));
        }
        for (ZsjosProductAttrSaveReqVO.Attr item : reqVO.getAttrs()) {
            ZsjosProductAttrDO attr = new ZsjosProductAttrDO();
            attr.setSpuId(reqVO.getSpuId()); attr.setAttrKey(item.getAttrKey()); attr.setAttrName(item.getAttrName());
            attr.setRequired(item.getRequired()); attr.setSort(item.getSort()); attr.setStatus(CommonStatusEnum.ENABLE.getStatus());
            attrMapper.insert(attr);
            for (ZsjosProductAttrSaveReqVO.Value itemValue : item.getValues()) {
                ZsjosProductAttrValueDO value = new ZsjosProductAttrValueDO();
                value.setAttrId(attr.getId()); value.setValue(itemValue.getValue()); value.setLabel(itemValue.getLabel());
                value.setSort(itemValue.getSort()); value.setStatus(CommonStatusEnum.ENABLE.getStatus());
                attrValueMapper.insert(value);
            }
        }
    }

    @Override @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long createSku(ZsjosProductSkuSaveReqVO reqVO) {
        lockEnabledProduct(reqVO.getSpuId());
        validatePrice(reqVO.getPrice());
        String json = canonicalAttrs(reqVO.getSpuId(), reqVO.getAttrValues());
        String hash = DigestUtil.sha256Hex(json);
        if (skuMapper.selectBySpuIdAndHash(reqVO.getSpuId(), hash) != null) throw exception(PRODUCT_SKU_DUPLICATE);
        ZsjosProductSkuDO sku = toSku(reqVO, json, hash);
        sku.setStatus(CommonStatusEnum.ENABLE.getStatus());
        sku.setSkuRef("sku_" + UUID.randomUUID().toString().replace("-", ""));
        skuMapper.insert(sku); return sku.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int generateSkus(Long spuId) {
        if (maxGeneratedCombinations <= 0) {
            throw exception(PRODUCT_SKU_COMBINATION_LIMIT);
        }
        ZsjosProductDO spu = lockEnabledProduct(spuId);
        List<ZsjosProductAttrDO> attrs = attrMapper.selectListBySpuId(spuId);
        Map<Long, List<ZsjosProductAttrValueDO>> values = attrValueMapper
                .selectListByAttrIds(attrs.stream().map(ZsjosProductAttrDO::getId).toList()).stream()
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .collect(Collectors.groupingBy(ZsjosProductAttrValueDO::getAttrId, LinkedHashMap::new, Collectors.toList()));
        long combinationsCount = 1;
        for (ZsjosProductAttrDO attr : attrs) {
            int valueCount = values.getOrDefault(attr.getId(), List.of()).size();
            if (valueCount == 0) return 0;
            if (combinationsCount > maxGeneratedCombinations / (long) valueCount) {
                throw exception(PRODUCT_SKU_COMBINATION_LIMIT);
            }
            combinationsCount *= valueCount;
        }
        List<Map<String, String>> combinations = new ArrayList<>(); combinations.add(new LinkedHashMap<>());
        for (ZsjosProductAttrDO attr : attrs) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> combination : combinations) for (ZsjosProductAttrValueDO value : values.getOrDefault(attr.getId(), List.of())) {
                Map<String, String> copy = new LinkedHashMap<>(combination); copy.put(attr.getAttrKey(), value.getValue()); next.add(copy);
            }
            combinations = next;
        }
        int created = 0;
        for (Map<String, String> combination : combinations) {
            String json = canonicalAttrs(spuId, combination); String hash = DigestUtil.sha256Hex(json);
            if (skuMapper.selectBySpuIdAndHash(spuId, hash) != null) continue;
            ZsjosProductSkuDO sku = new ZsjosProductSkuDO(); sku.setSpuId(spuId);
            sku.setSkuRef("sku_" + UUID.randomUUID().toString().replace("-", ""));
            sku.setSkuName(spu.getName() + (combination.isEmpty() ? "" : " - " + String.join(" / ", combination.values())));
            sku.setAttrValuesJson(json); sku.setAttrValuesHash(hash); sku.setPrice(BigDecimal.ZERO);
            sku.setStatus(CommonStatusEnum.ENABLE.getStatus()); sku.setSort(created); skuMapper.insert(sku); created++;
        }
        return created;
    }

    @Override @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void updateSku(ZsjosProductSkuSaveReqVO reqVO) {
        lockProduct(reqVO.getSpuId());
        ZsjosProductSkuDO existing = validateSkuExists(reqVO.getId());
        if (!Objects.equals(existing.getSpuId(), reqVO.getSpuId())) throw exception(PRODUCT_SKU_SPU_IMMUTABLE);
        validatePrice(reqVO.getPrice());
        String json = canonicalAttrs(reqVO.getSpuId(), reqVO.getAttrValues()); String hash = DigestUtil.sha256Hex(json);
        ZsjosProductSkuDO same = skuMapper.selectBySpuIdAndHash(reqVO.getSpuId(), hash);
        if (same != null && !Objects.equals(same.getId(), reqVO.getId())) throw exception(PRODUCT_SKU_DUPLICATE);
        ZsjosProductSkuDO update = toSku(reqVO, json, hash); update.setSkuRef(existing.getSkuRef());
        update.setStatus(null); skuMapper.updateById(update);
    }

    @Override @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteSku(Long id) {
        ZsjosProductSkuDO sku = lockSkuProduct(id);
        if (intendedProductMapper.selectCountBySkuRef(sku.getSkuRef()) > 0) throw exception(PRODUCT_SKU_IN_USE);
        skuMapper.deleteById(id);
    }

    @Override @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void updateSkuStatus(ZsjosProductSkuStatusReqVO reqVO) {
        ZsjosProductSkuDO existing = lockSkuProduct(reqVO.getId());
        if (!Set.of(CommonStatusEnum.ENABLE.getStatus(), CommonStatusEnum.DISABLE.getStatus()).contains(reqVO.getStatus())) {
            throw exception(PRODUCT_SKU_INVALID);
        }
        if (CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())) {
            ZsjosProductDO spu = lockEnabledProduct(existing.getSpuId());
            if (spu == null) throw exception(PRODUCT_SKU_INVALID);
            productService.validateEnabledProducts(List.of(spu.getProductRef()));
            canonicalAttrs(spu.getId(), parseAttrs(existing.getAttrValuesJson()));
        }
        ZsjosProductSkuDO update = new ZsjosProductSkuDO(); update.setId(reqVO.getId()); update.setStatus(reqVO.getStatus()); skuMapper.updateById(update);
    }

    @Override public ZsjosProductSkuRespVO getSku(Long id) { return toResp(validateSkuExists(id)); }
    @Override public List<ZsjosProductSkuRespVO> getSkuList(Long spuId) {
        var attrs = getAttrs(spuId);
        return skuMapper.selectListBySpuId(spuId).stream().map(sku -> toResp(sku, attrs)).toList();
    }

    @Override
    public LeadProductCatalogRespVO getLeadCatalog() {
        List<ZsjosProductSimpleRespVO> products = productService.getEnabledSimpleList();
        if (products.isEmpty()) return new LeadProductCatalogRespVO(List.of(), List.of(), List.of());
        Map<String, ZsjosProductDO> productByRef = productMapper.selectListByRefs(products.stream().map(ZsjosProductSimpleRespVO::productRef).toList())
                .stream().collect(Collectors.toMap(ZsjosProductDO::getProductRef, item -> item));
        Map<Long, List<ZsjosProductSkuDO>> skus = skuMapper.selectEnabledListBySpuIds(productByRef.values().stream().map(ZsjosProductDO::getId).toList())
                .stream().collect(Collectors.groupingBy(ZsjosProductSkuDO::getSpuId));
        List<LeadProductCatalogRespVO.Spu> spus = new ArrayList<>();
        List<LeadProductCatalogRespVO.Sku> catalogSkus = new ArrayList<>();
        for (ZsjosProductSimpleRespVO item : products) {
            ZsjosProductDO spu = productByRef.get(item.productRef());
            List<ZsjosProductAttrRespVO> attrs = getAttrs(spu.getId());
            spus.add(new LeadProductCatalogRespVO.Spu(item.categoryId(), item.categoryName(), item.categoryPath(),
                    item.level1CategoryId(), item.level1CategoryName(), item.level2CategoryId(), item.level2CategoryName(),
                    item.productRef(), item.name(),
                    attrs.stream().map(attr -> new LeadProductCatalogRespVO.Attr(attr.attrKey(), attr.attrName(), attr.required(),
                            attr.values().stream().map(value -> new LeadProductCatalogRespVO.Value(value.value(), value.label())).toList())).toList()));
            catalogSkus.addAll(skus.getOrDefault(spu.getId(), List.of()).stream().map(sku -> new LeadProductCatalogRespVO.Sku(
                    item.productRef(), sku.getSkuRef(), sku.getSkuName(), parseAttrs(sku.getAttrValuesJson()), sku.getPrice(),
                    ProductSpecVO.resolve(parseAttrs(sku.getAttrValuesJson()), attrs))).toList());
        }
        return new LeadProductCatalogRespVO(buildCategoryTree(products), spus, catalogSkus);
    }

    private List<LeadProductCatalogRespVO.Category> buildCategoryTree(List<ZsjosProductSimpleRespVO> products) {
        Map<Long, MutableCategory> nodes = new LinkedHashMap<>();
        Set<Long> childIds = new HashSet<>();
        for (ZsjosProductSimpleRespVO product : products) {
            MutableCategory parent = null;
            for (var pathNode : product.categoryPath()) {
                MutableCategory current = nodes.computeIfAbsent(pathNode.id(), id -> new MutableCategory(id, pathNode.name()));
                if (parent != null && parent.children.putIfAbsent(current.id, current) == null) childIds.add(current.id);
                parent = current;
            }
        }
        return nodes.values().stream().filter(item -> !childIds.contains(item.id)).map(MutableCategory::toResp).toList();
    }

    private static final class MutableCategory {
        private final Long id; private final String name;
        private final Map<Long, MutableCategory> children = new LinkedHashMap<>();
        private MutableCategory(Long id, String name) { this.id = id; this.name = name; }
        private LeadProductCatalogRespVO.Category toResp() {
            return new LeadProductCatalogRespVO.Category(id, name, children.values().stream().map(MutableCategory::toResp).toList());
        }
    }

    @Override
    public LeadProductSnapshot validateLeadProduct(String spuRef, boolean spuUnknown, String skuRef, boolean skuUnknown) {
        if (spuUnknown) {
            if (!skuUnknown || skuRef != null || spuRef != null) throw exception(PRODUCT_SKU_INVALID);
            return LeadProductSnapshot.unknown();
        }
        if (spuRef == null) throw exception(PRODUCT_REFS_INVALID);
        LeadProductSnapshot spu = productService.validateEnabledProducts(List.of(spuRef)).getFirst();
        if (skuUnknown) {
            if (skuRef != null) throw exception(PRODUCT_SKU_INVALID);
            return spu.withUnknownSku();
        }
        ZsjosProductDO product = productMapper.selectByProductRef(spuRef);
        ZsjosProductSkuDO sku = skuRef == null ? null : skuMapper.selectBySkuRef(skuRef);
        if (sku == null || !Objects.equals(sku.getSpuId(), product.getId())
                || !CommonStatusEnum.ENABLE.getStatus().equals(sku.getStatus())) throw exception(PRODUCT_SKU_INVALID);
        return spu.withSku(sku.getSkuRef(), sku.getSkuName(), sku.getAttrValuesJson(), sku.getPrice())
                .withSpecs(ProductSpecVO.resolve(parseAttrs(sku.getAttrValuesJson()), getAttrs(product.getId())));
    }

    private String canonicalAttrs(Long spuId, Map<String, String> requested) {
        List<ZsjosProductAttrDO> attrs = attrMapper.selectListBySpuId(spuId);
        Map<String, ZsjosProductAttrDO> byKey = attrs.stream().collect(Collectors.toMap(ZsjosProductAttrDO::getAttrKey, item -> item));
        if (!byKey.keySet().containsAll(requested.keySet())) throw exception(PRODUCT_ATTR_INVALID);
        Map<Long, Set<String>> values = attrValueMapper.selectListByAttrIds(attrs.stream().map(ZsjosProductAttrDO::getId).toList()).stream()
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .collect(Collectors.groupingBy(ZsjosProductAttrValueDO::getAttrId, Collectors.mapping(ZsjosProductAttrValueDO::getValue, Collectors.toSet())));
        for (ZsjosProductAttrDO attr : attrs) {
            String value = requested.get(attr.getAttrKey());
            if (Boolean.TRUE.equals(attr.getRequired()) && (value == null || value.isBlank())) throw exception(PRODUCT_ATTR_INVALID);
            if (value != null && !values.getOrDefault(attr.getId(), Set.of()).contains(value)) throw exception(PRODUCT_ATTR_INVALID);
        }
        return JsonUtils.toJsonString(new TreeMap<>(requested));
    }
    private void validatePrice(BigDecimal price) { if (price == null || price.signum() < 0) throw exception(PRODUCT_PRICE_INVALID); }
    private ZsjosProductDO lockProduct(Long spuId) {
        ZsjosProductDO product = productMapper.selectByIdForUpdate(spuId, TenantContextHolder.getRequiredTenantId());
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        return product;
    }
    private ZsjosProductDO lockEnabledProduct(Long spuId) {
        ZsjosProductDO product = lockProduct(spuId);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus())) throw exception(PRODUCT_REFS_INVALID);
        var categories = categoryLocks.paths(List.of(product.getCategoryId()));
        Set<Long> visited = new HashSet<>();
        Long categoryId = product.getCategoryId();
        int depth = 0;
        while (categoryId != null && categoryId != 0L && depth++ < 10 && visited.add(categoryId)) {
            ZsjosProductCategoryDO category = categories.get(categoryId);
            if (category == null || !CommonStatusEnum.ENABLE.getStatus().equals(category.getStatus())) {
                throw exception(PRODUCT_REFS_INVALID);
            }
            categoryId = category.getParentId();
        }
        if (depth == 0 || categoryId == null || categoryId != 0L) throw exception(PRODUCT_REFS_INVALID);
        return product;
    }
    private ZsjosProductSkuDO validateSkuExists(Long id) { ZsjosProductSkuDO sku = skuMapper.selectById(id); if (sku == null) throw exception(PRODUCT_SKU_NOT_EXISTS); return sku; }
    private ZsjosProductSkuDO lockSkuProduct(Long id) {
        Long productId = validateSkuExists(id).getSpuId();
        lockProduct(productId);
        var current = validateSkuExists(id);
        if (!Objects.equals(productId, current.getSpuId())) throw exception(PRODUCT_CATALOG_CHANGED);
        return current;
    }
    private ZsjosProductSkuDO toSku(ZsjosProductSkuSaveReqVO req, String json, String hash) {
        ZsjosProductSkuDO sku = new ZsjosProductSkuDO(); sku.setId(req.getId()); sku.setSpuId(req.getSpuId());
        sku.setSkuName(req.getSkuName()); sku.setAttrValuesJson(json); sku.setAttrValuesHash(hash); sku.setPrice(req.getPrice());
        sku.setStatus(req.getStatus()); sku.setSort(req.getSort()); sku.setRemark(req.getRemark()); return sku;
    }
    private ZsjosProductSkuRespVO toResp(ZsjosProductSkuDO sku) { return toResp(sku, getAttrs(sku.getSpuId())); }
    private ZsjosProductSkuRespVO toResp(ZsjosProductSkuDO sku, List<ZsjosProductAttrRespVO> attrs) { return new ZsjosProductSkuRespVO(sku.getId(), sku.getSpuId(), sku.getSkuRef(), sku.getSkuName(), parseAttrs(sku.getAttrValuesJson()), sku.getPrice(), sku.getStatus(), sku.getSort(), sku.getRemark(), sku.getUpdateTime(), ProductSpecVO.resolve(parseAttrs(sku.getAttrValuesJson()), attrs)); }
    @SuppressWarnings("unchecked") private Map<String, String> parseAttrs(String json) { return json == null ? Map.of() : JsonUtils.parseObject(json, Map.class); }
}
