package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import java.util.*;

/** Display labels travel with business snapshots; current metadata is only used for new selections. */
public record ProductSpecVO(String attrKey, String attrName, String value, String label, boolean labelMissing) {
    public String displayText() {
        return attrName + "：" + label + (labelMissing ? "（历史标签缺失）" : "");
    }

    public static List<ProductSpecVO> resolve(Map<String, String> selected, List<ZsjosProductAttrRespVO> attrs) {
        Map<String, String> remaining = new LinkedHashMap<>(selected == null ? Map.of() : selected);
        List<ProductSpecVO> result = new ArrayList<>();
        for (var attr : attrs) {
            String value = remaining.remove(attr.attrKey());
            if (value == null) continue;
            var option = attr.values().stream().filter(v -> Objects.equals(v.value(), value)).findFirst();
            result.add(new ProductSpecVO(attr.attrKey(), attr.attrName(), value,
                    option.map(ZsjosProductAttrRespVO.Value::label).orElse(value), option.isEmpty()));
        }
        remaining.forEach((key, value) -> result.add(new ProductSpecVO(key, key, value, value, true)));
        return List.copyOf(result);
    }
}
