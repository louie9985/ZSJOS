package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal;

import lombok.Data;
import java.util.List;

@Data
public class LeadAppealContextRespVO {
    private Long invalidEventId;
    private Integer nextRoundNo;
    private Boolean canSubmit;
    private List<LeadAppealRespVO> records;
}
