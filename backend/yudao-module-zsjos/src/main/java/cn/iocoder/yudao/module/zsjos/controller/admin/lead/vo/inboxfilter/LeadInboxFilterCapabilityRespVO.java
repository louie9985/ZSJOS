package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadInboxFilterCapabilityRespVO {
    private String field;
    private String label;
    private List<ValueVO> values;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueVO {
        private String value;
        private String label;
    }
}
