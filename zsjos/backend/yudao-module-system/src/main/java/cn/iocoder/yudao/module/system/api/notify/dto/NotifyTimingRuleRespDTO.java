package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyTimingRuleRespDTO {
    private Long id;
    private String sceneCode;
    private String timingStage;
    private Integer timingOffsetMinutes;
}
