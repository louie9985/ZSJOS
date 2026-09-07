package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo;

import java.util.List;

public record AdvancedFilterCatalogRespVO(List<FieldVO> fields, List<OptionVO> relativeDateOptions) {
    public record FieldVO(String fieldKey, String group, String label, String valueType,
                          List<String> operators, String optionSource, List<OptionVO> options,
                          String optionsState, String optionsErrorCode) {
        public FieldVO(String fieldKey, String group, String label, String valueType,
                       List<String> operators, String optionSource, List<OptionVO> options) {
            this(fieldKey, group, label, valueType, operators, optionSource, options,
                    options == null || options.isEmpty() ? "empty" : "ready", null);
        }
    }
    public record OptionVO(String value, String label) {}
}
