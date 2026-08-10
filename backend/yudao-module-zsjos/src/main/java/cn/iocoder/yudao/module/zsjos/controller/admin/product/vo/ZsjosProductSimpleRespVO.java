package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import java.util.List;

public record ZsjosProductSimpleRespVO(String productRef, String name, Long categoryId,
                                       String categoryName, List<ZsjosProductCategoryPathNodeVO> categoryPath,
                                       Long level1CategoryId, String level1CategoryName,
                                       Long level2CategoryId, String level2CategoryName) {}
