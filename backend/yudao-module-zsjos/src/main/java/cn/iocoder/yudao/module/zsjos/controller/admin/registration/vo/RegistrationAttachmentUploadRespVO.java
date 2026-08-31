package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegistrationAttachmentUploadRespVO {
    private Long id;
    private Long infraFileId;
    private String fileUrl;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Integer version;
}
