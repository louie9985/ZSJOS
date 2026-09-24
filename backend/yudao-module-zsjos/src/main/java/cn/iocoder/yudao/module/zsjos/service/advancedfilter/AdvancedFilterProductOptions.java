package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/** Scene authorization grants filter choices only; prices and product administration are not exposed. */
@Service
public class AdvancedFilterProductOptions {
    @Resource private ZsjosProductSkuService products;

    public AdvancedFilterCatalogRespVO resolve(AdvancedFilterCatalogRespVO catalog) {
        if (catalog.fields().stream().noneMatch(field -> field.optionSource() != null
                && field.optionSource().startsWith("product-catalog:"))) return catalog;
        var source = products.getLeadCatalog();
        var names = source.spus().stream().collect(Collectors.toMap(
                item -> item.spuRef(), item -> item.spuName(), (first, second) -> first));
        var options = Map.of(
                "product-catalog:spu", source.spus().stream().map(item ->
                        new AdvancedFilterCatalogRespVO.OptionVO(item.spuRef(), item.spuName())).toList(),
                "product-catalog:sku", source.skus().stream().map(item ->
                        new AdvancedFilterCatalogRespVO.OptionVO(item.skuRef(),
                                names.getOrDefault(item.spuRef(), item.spuRef()) + " / " + item.skuName())).toList());
        return new AdvancedFilterCatalogRespVO(catalog.fields().stream().map(field ->
                field.optionSource() != null && options.containsKey(field.optionSource())
                        ? field.withResolvedOptions(options.get(field.optionSource())) : field).toList(),
                catalog.relativeDateOptions());
    }
}
