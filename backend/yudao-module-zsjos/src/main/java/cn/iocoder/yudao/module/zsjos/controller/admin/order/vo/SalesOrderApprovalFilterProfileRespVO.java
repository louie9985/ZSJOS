package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderApprovalFilterProfileRespVO {
    private List<GroupVO> groups;
    private List<CenterVO> centers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CenterVO {
        private String key;
        private String label;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupVO {
        private String key;
        private String label;
        private Long count;
        private List<SectionVO> sections;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionVO {
        private String key;
        private String label;
        private List<OptionVO> options;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionVO {
        private String key;
        private String label;
        private Long count;
    }
}
