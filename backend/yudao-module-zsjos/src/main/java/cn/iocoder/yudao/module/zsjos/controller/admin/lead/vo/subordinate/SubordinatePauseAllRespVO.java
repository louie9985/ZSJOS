package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubordinatePauseAllRespVO {
    private int totalCount;
    private int changedCount;
    private int alreadyPausedCount;
}
