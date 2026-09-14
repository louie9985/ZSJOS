package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record ZsjosProductSkuRespVO(Long id, Long spuId, String skuRef, String skuName,
                                    Map<String, String> attrValues, BigDecimal price, BigDecimal retailPrice,
                                    BigDecimal minDealPrice, String minDealType, BigDecimal minDealRate,
                                    BigDecimal examFee, String priceUnit, String pricingNote, Integer status,
                                    Integer sort, String remark, LocalDateTime updateTime,
                                    java.util.List<ProductSpecVO> specs) {}
