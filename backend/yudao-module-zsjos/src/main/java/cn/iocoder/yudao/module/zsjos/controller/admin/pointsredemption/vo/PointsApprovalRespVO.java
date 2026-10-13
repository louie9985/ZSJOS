package cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import lombok.Data;

@Data
public class PointsApprovalRespVO {
    private String availability;
    private String unavailableReason;
    private BpmProcessProgressDTO progress;
    private boolean canUrge;
    private Long nextUrgeAt;
}
