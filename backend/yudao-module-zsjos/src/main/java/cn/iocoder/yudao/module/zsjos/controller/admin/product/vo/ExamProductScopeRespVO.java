package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import java.util.List;
import java.util.Map;

/** A product-owned, price-free projection for exam selection and publication snapshots. */
public record ExamProductScopeRespVO(Long productId, String productRef, String productName, Long categoryId,
        List<ZsjosProductCategoryPathNodeVO> categoryPath, List<ZsjosProductAttrRespVO> attrs,
        List<ProductSpecVO> selectedSpecs, List<Sku> skus) {
    public record Sku(Long id, String skuRef, String skuName, Map<String, String> attrValues,
                      List<ProductSpecVO> specs) {}
}
