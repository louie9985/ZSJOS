package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.util.List;

@Data
public class PartnerLeaderboardConfigRespVO {
    private Boolean enabled;
    private List<String> enabledTypes;
    private String defaultType;
    private String defaultPeriod;
    private Integer pageSize;
    private Boolean maskName;
    private List<TypeOption> typeOptions;

    @Data
    public static class TypeOption {
        private String key;
        private String label;
        private String valueLabel;
        private String valueUnit;
        private String ruleText;
    }
}
