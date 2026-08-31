package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record LeadProductCatalogRespVO(List<Category> categoryTree, List<Spu> spus, List<Sku> skus) {
    public record Category(Long id, String name, List<Category> children) {}
    public record Spu(Long categoryId, String categoryName, List<ZsjosProductCategoryPathNodeVO> categoryPath,
                      Long level1CategoryId, String level1CategoryName, Long level2CategoryId,
                      String level2CategoryName, String spuRef, String spuName, List<Attr> attrs) {}
    public record Attr(String attrKey, String attrName, Boolean required, List<Value> values) {}
    public record Value(String value, String label) {}
    public record Sku(String spuRef, String skuRef, String skuName, Map<String, String> attrValues, BigDecimal price) {}
}
