package cn.iocoder.yudao.module.zsjos.service.lead.product;

import java.math.BigDecimal;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;

public record LeadProductSnapshot(String productRef, String name, Long categoryId, String categoryName,
                                  List<ZsjosProductCategoryPathNodeVO> categoryPath, Long level1CategoryId,
                                  String level1CategoryName, Long level2CategoryId,
                                  String level2CategoryName, String skuRef, String skuName,
                                  String selectedAttrValuesJson, BigDecimal price, boolean spuUnknown,
                                  boolean skuUnknown) {
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
