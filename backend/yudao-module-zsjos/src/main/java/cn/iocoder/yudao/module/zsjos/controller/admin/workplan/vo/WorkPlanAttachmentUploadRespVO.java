package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkPlanAttachmentUploadRespVO {
    private Long infraFileId;
    private String originalName;
    private String contentType;
    private Long fileSize;
}
