package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
public class SubordinateBatchResultVO {
    private int successCount;
    private int failureCount;
    private List<ItemVO> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemVO {
        private Long leadId;
        private String leadNo;
        private Boolean success;
        private String code;
        private String message;
    }
}
