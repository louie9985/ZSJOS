package cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document;

import lombok.Data;

@Data
public class PmsKnowledgeAttachmentRespVO {
    private Long fileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String url;
}
