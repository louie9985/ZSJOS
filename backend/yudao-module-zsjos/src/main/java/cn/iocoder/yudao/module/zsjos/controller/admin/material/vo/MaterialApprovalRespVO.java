package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import lombok.Data;
@Data
public class MaterialApprovalRespVO {
    private BpmTaskRespDTO task;
    private Long versionId;
    private String materialNo;
    private String title;
    private boolean snapshotAvailable;
    private MaterialVersionRespVO snapshot;
}
