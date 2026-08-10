package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import java.util.List;

public record ZsjosProductAttrRespVO(String attrKey, String attrName, Boolean required, Integer sort,
                                     List<Value> values) {
    public record Value(String value, String label, Integer sort) {}
}
