package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo;

import java.util.List;

public record AdvancedFilterCatalogRespVO(List<FieldVO> fields) {
    public record FieldVO(String fieldKey, String group, String label, String valueType,
                          List<String> operators, String optionSource, List<OptionVO> options) {}
    public record OptionVO(String value, String label) {}
}
