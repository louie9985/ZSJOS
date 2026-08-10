package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LeadAttachmentUploadRespVO {
    private Long infraFileId;
    private String fileUrl;
    private String originalName;
    private String contentType;
    private Long fileSize;
}
