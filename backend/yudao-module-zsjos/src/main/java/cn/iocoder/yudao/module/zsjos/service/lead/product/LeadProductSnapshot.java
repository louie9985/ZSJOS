package cn.iocoder.yudao.module.zsjos.service.lead.product;

import java.math.BigDecimal;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;

public record LeadProductSnapshot(String productRef, String name, Long categoryId, String categoryName,
                                  List<ZsjosProductCategoryPathNodeVO> categoryPath, Long level1CategoryId,
                                  String level1CategoryName, Long level2CategoryId,
                                  String level2CategoryName, String skuRef, String skuName,
                                  String selectedAttrValuesJson, BigDecimal price, boolean spuUnknown,
                                  boolean skuUnknown, List<ProductSpecVO> specs) {
    public LeadProductSnapshot(String productRef, String name, Long categoryId, String categoryName,
            List<ZsjosProductCategoryPathNodeVO> categoryPath, Long level1CategoryId, String level1CategoryName,
            Long level2CategoryId, String level2CategoryName, String skuRef, String skuName,
            String selectedAttrValuesJson, BigDecimal price, boolean spuUnknown, boolean skuUnknown) {
        this(productRef, name, categoryId, categoryName, categoryPath, level1CategoryId, level1CategoryName,
                level2CategoryId, level2CategoryName, skuRef, skuName, selectedAttrValuesJson, price, spuUnknown, skuUnknown, null);
    }

    public LeadProductSnapshot withSpecs(List<ProductSpecVO> specs) {
        return new LeadProductSnapshot(productRef, name, categoryId, categoryName, categoryPath, level1CategoryId,
                level1CategoryName, level2CategoryId, level2CategoryName, skuRef, skuName,
                selectedAttrValuesJson, price, spuUnknown, skuUnknown, specs);
    }

    /** Rehydrates an existing lead selection without consulting the current product catalog. */
    public static LeadProductSnapshot fromIntendedProduct(LeadIntendedProductDO source) {
        List<ZsjosProductCategoryPathNodeVO> path = source.getCategoryPathSnapshot() == null
                ? List.of() : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(
                source.getCategoryPathSnapshot(), ZsjosProductCategoryPathNodeVO.class);
        List<ProductSpecVO> specs = source.getSelectedSpecsJson() == null
                ? null : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(
                source.getSelectedSpecsJson(), ProductSpecVO.class);
        return new LeadProductSnapshot(source.getSpuRef(), source.getSpuNameSnapshot(), source.getCategoryId(),
                source.getCategoryNameSnapshot(), path, source.getLevel1CategoryId(), source.getLevel1CategoryNameSnapshot(),
                source.getLevel2CategoryId(), source.getLevel2CategoryNameSnapshot(), source.getSkuRef(),
                source.getSkuNameSnapshot(), source.getSelectedAttrValuesJson(), source.getPriceSnapshot(),
                Boolean.TRUE.equals(source.getSpuUnknown()), Boolean.TRUE.equals(source.getSkuUnknown()), specs);
    }

    public LeadProductSnapshot retainSelection(List<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO> existing) {
        var old = existing.stream().filter(p -> java.util.Objects.equals(p.getSpuRef(), productRef)
                && java.util.Objects.equals(p.getSkuRef(), skuRef)
                && Boolean.TRUE.equals(p.getSpuUnknown()) == spuUnknown
                && Boolean.TRUE.equals(p.getSkuUnknown()) == skuUnknown).findFirst();
        if (old.isEmpty()) return this;
        var p = old.get();
        return new LeadProductSnapshot(p.getSpuRef(), p.getSpuNameSnapshot(), p.getCategoryId(), p.getCategoryNameSnapshot(),
                p.getCategoryPathSnapshot() == null ? List.of() : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(p.getCategoryPathSnapshot(), ZsjosProductCategoryPathNodeVO.class),
                p.getLevel1CategoryId(), p.getLevel1CategoryNameSnapshot(), p.getLevel2CategoryId(), p.getLevel2CategoryNameSnapshot(),
                p.getSkuRef(), p.getSkuNameSnapshot(), p.getSelectedAttrValuesJson(), price, spuUnknown, skuUnknown,
                p.getSelectedSpecsJson() == null ? null : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(p.getSelectedSpecsJson(), ProductSpecVO.class));
    }

    public List<ProductSpecVO> displaySpecs() {
        if (specs != null) return specs;
        java.util.Map<?, ?> values = selectedAttrValuesJson == null ? null
                : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObjectQuietly(selectedAttrValuesJson, java.util.Map.class);
        java.util.Map<String, String> raw = new java.util.LinkedHashMap<>();
        if (values != null) values.forEach((key, value) -> {
            if (key != null && value != null) raw.put(String.valueOf(key), String.valueOf(value));
        });
        return ProductSpecVO.resolve(raw, List.of());
    }
    public LeadProductSnapshot(String productRef, String name, Long level1CategoryId,
                               String level1CategoryName, Long level2CategoryId, String level2CategoryName) {
        this(productRef, name, level2CategoryId, level2CategoryName, List.of(), level1CategoryId, level1CategoryName, level2CategoryId, level2CategoryName,
                null, null, null, null, false, true);
    }
    public static LeadProductSnapshot of(String productRef, String name, List<ZsjosProductCategoryPathNodeVO> path) {
        ZsjosProductCategoryPathNodeVO first = path.getFirst();
        ZsjosProductCategoryPathNodeVO second = path.size() > 1 ? path.get(1) : null;
        ZsjosProductCategoryPathNodeVO leaf = path.getLast();
        return new LeadProductSnapshot(productRef, name, leaf.id(), leaf.name(), path, first.id(), first.name(),
                second == null ? null : second.id(), second == null ? null : second.name(),
                null, null, null, null, false, true);
    }
    public static LeadProductSnapshot unknown() {
        return new LeadProductSnapshot(null, "未明确课程", null, null, List.of(), null, null, null, null,
                null, "未明确具体班次/方案", null, null, true, true);
    }
    public LeadProductSnapshot withUnknownSku() {
        return new LeadProductSnapshot(productRef, name, categoryId, categoryName, categoryPath, level1CategoryId, level1CategoryName,
                level2CategoryId, level2CategoryName, null, "未明确具体班次/方案", null, null, false, true);
    }
    public LeadProductSnapshot withSku(String ref, String skuName, String attrs, BigDecimal price) {
        return new LeadProductSnapshot(productRef, name, categoryId, categoryName, categoryPath, level1CategoryId, level1CategoryName,
                level2CategoryId, level2CategoryName, ref, skuName, attrs, price, false, false);
    }
}
