package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

@Data
public class ForcedFormAttachmentUploadRespVO {

    private Long formId;
    private Long versionId;
    private String fieldKey;
    private Long infraFileId;
    private String uploadToken;
    private String fileName;
    private Long fileSize;
    private String contentType;

}
