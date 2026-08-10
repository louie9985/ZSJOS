package cn.iocoder.yudao.module.zsjos.service.product;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ZsjosProductSkuServiceImpl implements ZsjosProductSkuService {
    @Resource private ZsjosProductService productService;
    @Resource private ZsjosProductMapper productMapper;
    @Resource private ZsjosProductAttrMapper attrMapper;
    @Resource private ZsjosProductAttrValueMapper attrValueMapper;
    @Resource private ZsjosProductSkuMapper skuMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAttrs(ZsjosProductAttrSaveReqVO reqVO) {
        productService.getProduct(reqVO.getSpuId());
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

    @Override @Transactional(rollbackFor = Exception.class)
    public Long createSku(ZsjosProductSkuSaveReqVO reqVO) {
        productService.getProduct(reqVO.getSpuId());
        validatePrice(reqVO.getPrice());
        String json = canonicalAttrs(reqVO.getSpuId(), reqVO.getAttrValues());
        String hash = DigestUtil.sha256Hex(json);
        if (skuMapper.selectBySpuIdAndHash(reqVO.getSpuId(), hash) != null) throw exception(PRODUCT_SKU_DUPLICATE);
        ZsjosProductSkuDO sku = toSku(reqVO, json, hash);
        sku.setSkuRef("sku_" + UUID.randomUUID().toString().replace("-", ""));
        skuMapper.insert(sku); return sku.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateSkus(Long spuId) {
        ZsjosProductRespVO spu = productService.getProduct(spuId);
        List<ZsjosProductAttrDO> attrs = attrMapper.selectListBySpuId(spuId);
        Map<Long, List<ZsjosProductAttrValueDO>> values = attrValueMapper
                .selectListByAttrIds(attrs.stream().map(ZsjosProductAttrDO::getId).toList()).stream()
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .collect(Collectors.groupingBy(ZsjosProductAttrValueDO::getAttrId, LinkedHashMap::new, Collectors.toList()));
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
            sku.setStatus(CommonStatusEnum.DISABLE.getStatus()); sku.setSort(created); skuMapper.insert(sku); created++;
        }
        return created;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateSku(ZsjosProductSkuSaveReqVO reqVO) {
        ZsjosProductSkuDO existing = validateSkuExists(reqVO.getId());
        productService.getProduct(reqVO.getSpuId()); validatePrice(reqVO.getPrice());
        String json = canonicalAttrs(reqVO.getSpuId(), reqVO.getAttrValues()); String hash = DigestUtil.sha256Hex(json);
        ZsjosProductSkuDO same = skuMapper.selectBySpuIdAndHash(reqVO.getSpuId(), hash);
        if (same != null && !Objects.equals(same.getId(), reqVO.getId())) throw exception(PRODUCT_SKU_DUPLICATE);
        ZsjosProductSkuDO update = toSku(reqVO, json, hash); update.setSkuRef(existing.getSkuRef()); skuMapper.updateById(update);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void deleteSku(Long id) {
        ZsjosProductSkuDO sku = validateSkuExists(id);
        if (intendedProductMapper.selectCountBySkuRef(sku.getSkuRef()) > 0) throw exception(PRODUCT_SKU_IN_USE);
        skuMapper.deleteById(id);
    }

    @Override public void updateSkuStatus(ZsjosProductSkuStatusReqVO reqVO) {
        ZsjosProductSkuDO existing = validateSkuExists(reqVO.getId());
        if (!Set.of(CommonStatusEnum.ENABLE.getStatus(), CommonStatusEnum.DISABLE.getStatus()).contains(reqVO.getStatus())) {
            throw exception(PRODUCT_SKU_INVALID);
        }
        if (CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())) {
            ZsjosProductDO spu = productMapper.selectById(existing.getSpuId());
            if (spu == null) throw exception(PRODUCT_SKU_INVALID);
            productService.validateEnabledProducts(List.of(spu.getProductRef()));
            canonicalAttrs(spu.getId(), parseAttrs(existing.getAttrValuesJson()));
        }
        ZsjosProductSkuDO update = new ZsjosProductSkuDO(); update.setId(reqVO.getId()); update.setStatus(reqVO.getStatus()); skuMapper.updateById(update);
    }

    @Override public ZsjosProductSkuRespVO getSku(Long id) { return toResp(validateSkuExists(id)); }
    @Override public List<ZsjosProductSkuRespVO> getSkuList(Long spuId) {
        productService.getProduct(spuId); return skuMapper.selectListBySpuId(spuId).stream().map(this::toResp).toList();
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
                    item.productRef(), sku.getSkuRef(), sku.getSkuName(), parseAttrs(sku.getAttrValuesJson()), sku.getPrice())).toList());
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
        return spu.withSku(sku.getSkuRef(), sku.getSkuName(), sku.getAttrValuesJson(), sku.getPrice());
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
    private ZsjosProductSkuDO validateSkuExists(Long id) { ZsjosProductSkuDO sku = skuMapper.selectById(id); if (sku == null) throw exception(PRODUCT_SKU_NOT_EXISTS); return sku; }
    private ZsjosProductSkuDO toSku(ZsjosProductSkuSaveReqVO req, String json, String hash) {
        ZsjosProductSkuDO sku = new ZsjosProductSkuDO(); sku.setId(req.getId()); sku.setSpuId(req.getSpuId());
        sku.setSkuName(req.getSkuName()); sku.setAttrValuesJson(json); sku.setAttrValuesHash(hash); sku.setPrice(req.getPrice());
        sku.setStatus(req.getStatus()); sku.setSort(req.getSort()); sku.setRemark(req.getRemark()); return sku;
    }
    private ZsjosProductSkuRespVO toResp(ZsjosProductSkuDO sku) { return new ZsjosProductSkuRespVO(sku.getId(), sku.getSpuId(), sku.getSkuRef(), sku.getSkuName(), parseAttrs(sku.getAttrValuesJson()), sku.getPrice(), sku.getStatus(), sku.getSort(), sku.getRemark(), sku.getUpdateTime()); }
    @SuppressWarnings("unchecked") private Map<String, String> parseAttrs(String json) { return json == null ? Map.of() : JsonUtils.parseObject(json, Map.class); }
}
