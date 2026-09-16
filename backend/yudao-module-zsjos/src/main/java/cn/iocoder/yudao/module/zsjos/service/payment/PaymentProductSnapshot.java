package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;

import java.math.BigDecimal;
import java.util.List;

/** Display data frozen when a payment link is created; old snapshots may lack names and specs. */
public record PaymentProductSnapshot(String spuRef, String skuRef, BigDecimal actualAmount,
                                     String skuName, String productName, List<ProductSpecVO> specs) {
}
