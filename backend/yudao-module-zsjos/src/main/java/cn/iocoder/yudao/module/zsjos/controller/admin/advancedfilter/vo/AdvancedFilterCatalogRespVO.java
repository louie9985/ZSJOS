package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo;

import java.util.List;

public record AdvancedFilterCatalogRespVO(List<FieldVO> fields, List<OptionVO> relativeDateOptions) {
    public record FieldVO(String fieldKey, String group, String label, String valueType,
                          List<String> operators, String optionSource, List<OptionVO> options,
                          String optionsState, String optionsErrorCode,
                          List<String> supportedScenes, List<String> supportedPages,
                          String permission, String dataScope, boolean sensitive, String sensitivity,
                          boolean sortable, boolean deprecated, String optionSourceType,
                          String declaredOptionSource) {
        public FieldVO {
            operators = List.copyOf(operators);
            options = List.copyOf(options);
            supportedScenes = List.copyOf(supportedScenes);
            supportedPages = List.copyOf(supportedPages);
        }

        /** Resolved entity options must retain field policy and their original authoritative source. */
        public FieldVO withResolvedOptions(List<OptionVO> resolved) {
            return new FieldVO(fieldKey, group, label, valueType, operators, null, resolved,
                    resolved.isEmpty() ? "empty" : "ready", null, supportedScenes, supportedPages,
                    permission, dataScope, sensitive, sensitivity, sortable, deprecated,
                    optionSourceType, declaredOptionSource);
        }
    }
    public record OptionVO(String value, String label) {}
}
